import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClient, ApiError, decodeUser } from './client';

const token = (name: string) => `header.${btoa(JSON.stringify({ sub: 'user-id', fullName: name, email: 'ana@example.com' }))}.signature`;
const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });
const tokens = (name: string) => ({ accessToken: token(name), refreshToken: `refresh-${name}` });
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

beforeEach(() => sessionStorage.clear());
describe('HTTP authentication', () => {
  it('shares one refresh across simultaneous 401 responses and stores the rotated token', async () => {
    const gate = deferred<Response>();
    const transport = vi.fn<typeof fetch>().mockImplementation(async (path, init) => {
      if (path === '/auth/login') return json(tokens('old'));
      if (path === '/auth/refresh') return gate.promise;
      return new Headers(init?.headers).get('Authorization') === `Bearer ${token('new')}` ? json({ ok: true }) : json({}, 401);
    });
    const client = new ApiClient(transport);
    await client.login('ana@example.com', 'password');
    const first = client.request('/accounts');
    const second = client.request('/transactions');
    await vi.waitFor(() => expect(transport.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1));
    gate.resolve(json(tokens('new')));
    await expect(Promise.all([first, second])).resolves.toEqual([{ ok: true }, { ok: true }]);
    expect(sessionStorage.getItem('fraudshield.refreshToken')).toBe('refresh-new');
    expect(transport.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1);
    expect(sessionStorage.getItem('accessToken')).toBeNull();
  });

  it('reuses the renewed access token for a delayed 401 without rotating again', async () => {
    const late = deferred<Response>();
    let lateCalls = 0;
    const transport = vi.fn<typeof fetch>().mockImplementation(async (path, init) => {
      if (path === '/auth/login') return json(tokens('old'));
      if (path === '/auth/refresh') return json(tokens('new'));
      if (path === '/late' && lateCalls++ === 0) return late.promise;
      return new Headers(init?.headers).get('Authorization') === `Bearer ${token('new')}` ? json({ ok: true }) : json({}, 401);
    });
    const client = new ApiClient(transport);
    await client.login('', '');
    const pending = client.request('/late');
    await client.request('/accounts');
    late.resolve(json({}, 401));
    await pending;
    expect(transport.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1);
  });

  it('clears the session and does not repeat the original request when refresh fails', async () => {
    const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => path === '/auth/login' ? json(tokens('old')) : json({}, 401));
    const client = new ApiClient(transport);
    await client.login('', '');
    await expect(client.request('/accounts')).rejects.toBeInstanceOf(ApiError);
    expect(client.snapshot().user).toBeNull();
    expect(sessionStorage.length).toBe(0);
    expect(transport.mock.calls.filter(([path]) => path === '/accounts')).toHaveLength(1);
  });

  it('retries an authenticated request only once', async () => {
    const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => path === '/auth/login' ? json(tokens('old')) : path === '/auth/refresh' ? json(tokens('new')) : json({}, 401));
    const client = new ApiClient(transport);
    await client.login('', '');
    await expect(client.request('/accounts')).rejects.toMatchObject({ status: 401 });
    expect(transport.mock.calls.filter(([path]) => path === '/accounts')).toHaveLength(2);
    expect(client.snapshot().user).toBeNull();
  });

  it('preserves the session for a wrong current password after one retry', async () => {
    const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => path === '/auth/login' ? json(tokens('old')) : path === '/auth/refresh' ? json(tokens('new')) : json({ message: 'Invalid current password' }, 401));
    const client = new ApiClient(transport);
    await client.login('', '');
    await expect(client.request('/auth/password', { method: 'PUT', keepSessionOnUnauthorized: true })).rejects.toMatchObject({ status: 401 });
    expect(client.snapshot().user?.fullName).toBe('new');
  });

  it('does not refresh public requests or attach their access token', async () => {
    const transport = vi.fn<typeof fetch>().mockResolvedValue(json({}, 401));
    const client = new ApiClient(transport);
    await expect(client.login('', '')).rejects.toMatchObject({ status: 401 });
    expect(transport).toHaveBeenCalledTimes(1);
    expect(new Headers(transport.mock.calls[0]?.[1]?.headers).has('Authorization')).toBe(false);
  });

  it('bootstraps only once, including concurrent calls', async () => {
    sessionStorage.setItem('fraudshield.refreshToken', 'stored');
    const transport = vi.fn<typeof fetch>().mockResolvedValue(json(tokens('restored')));
    const client = new ApiClient(transport);
    await Promise.all([client.bootstrap(), client.bootstrap()]);
    expect(transport).toHaveBeenCalledTimes(1);
    expect(client.snapshot()).toMatchObject({ ready: true, user: { fullName: 'restored' } });
  });

  it('does not restore tokens after the session has been cleared during refresh', async () => {
    sessionStorage.setItem('fraudshield.refreshToken', 'stored');
    const gate = deferred<Response>();
    const client = new ApiClient(vi.fn<typeof fetch>().mockReturnValue(gate.promise));
    const pending = client.refresh();
    client.clear();
    gate.resolve(json(tokens('late')));
    await expect(pending).rejects.toMatchObject({ status: 401 });
    expect(client.snapshot().user).toBeNull();
    expect(sessionStorage.length).toBe(0);
  });

  it('handles empty 204 and non-JSON gateway errors', async () => {
    const transport = vi.fn<typeof fetch>().mockResolvedValueOnce(new Response(null, { status: 204 })).mockResolvedValueOnce(new Response('Unavailable', { status: 503 }));
    const client = new ApiClient(transport);
    await expect(client.request('/auth/logout', { public: true })).resolves.toBeUndefined();
    await expect(client.request('/accounts/deposit', { public: true })).rejects.toMatchObject({ status: 503 });
  });

  it('decodes names encoded as UTF-8 in JWT claims', () => {
    const payload = btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify({ sub: 'id', fullName: 'João', email: 'joao@example.com' }))));
    expect(decodeUser(`header.${payload}.signature`).fullName).toBe('João');
  });
});

it('clears all pending callers on a failed shared refresh', async () => {
  const gate = deferred<Response>();
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => path === '/auth/login' ? json(tokens('old')) : path === '/auth/refresh' ? gate.promise : json({}, 401));
  const client = new ApiClient(transport);
  await client.login('', '');
  const result = Promise.allSettled([client.request('/accounts'), client.request('/transactions')]);
  await vi.waitFor(() => expect(transport.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1));
  gate.resolve(json({}, 401));
  expect((await result).every((item) => item.status === 'rejected')).toBe(true);
  expect(client.snapshot().user).toBeNull();
  expect(transport.mock.calls.filter(([path]) => path === '/auth/refresh')).toHaveLength(1);
});

it('revokes the newest refresh token when logout overlaps rotation', async () => {
  const gate = deferred<Response>();
  const transport = vi.fn<typeof fetch>().mockImplementation(async (path) => path === '/auth/login' ? json(tokens('old')) : path === '/auth/refresh' ? gate.promise : new Response(null, { status: 204 }));
  const client = new ApiClient(transport);
  await client.login('', '');
  const refresh = client.refresh();
  const logout = client.logout();
  gate.resolve(json(tokens('new')));
  await Promise.all([refresh, logout]);
  const call = transport.mock.calls.find(([path]) => path === '/auth/logout');
  expect(JSON.parse(String(call?.[1]?.body))).toEqual({ refreshToken: 'refresh-new' });
  expect(client.snapshot().user).toBeNull();
});

it('treats a broken response body as an uncertain network outcome', async () => {
  const response = new Response(null, { status: 201 });
  vi.spyOn(response, 'text').mockRejectedValue(new TypeError('Connection interrupted'));
  const client = new ApiClient(vi.fn<typeof fetch>().mockResolvedValue(response));
  await expect(client.request('/accounts/deposit', { public: true })).rejects.toThrow('Não foi possível confirmar');
});
