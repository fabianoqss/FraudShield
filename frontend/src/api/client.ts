export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public fieldErrors: Record<string, string> = {},
    public retryAfter: string | null = null,
  ) {
    super(message);
  }
}

export class NetworkError extends Error {
  constructor() {
    super("Não foi possível confirmar a resposta. Verifique sua conexão.");
  }
}

export interface User {
  sub: string;
  fullName: string;
  email: string;
}
interface Tokens {
  accessToken: string;
  refreshToken: string;
}
interface Session {
  user: User | null;
  ready: boolean;
}
interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  public?: boolean;
  keepSessionOnUnauthorized?: boolean;
}
const refreshKey = "fraudshield.refreshToken";

export function decodeUser(token: string): User {
  const payload = token.split(".")[1];
  if (!payload) throw new Error("Token inválido.");
  const base64 = payload.replace(/-/g, "+").replace(/_/g, "/");
  const bytes = Uint8Array.from(
    atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "=")),
    (char) => char.charCodeAt(0),
  );
  const value: unknown = JSON.parse(new TextDecoder().decode(bytes));
  if (
    !value ||
    typeof value !== "object" ||
    !("sub" in value) ||
    !("fullName" in value) ||
    !("email" in value) ||
    typeof value.sub !== "string" ||
    typeof value.fullName !== "string" ||
    typeof value.email !== "string"
  ) {
    throw new Error("Token inválido.");
  }
  return { sub: value.sub, fullName: value.fullName, email: value.email };
}

export class ApiClient {
  private accessToken: string | null = null;
  private session: Session = { user: null, ready: false };
  private listeners = new Set<() => void>();
  private refreshFlight: Promise<void> | null = null;
  private bootstrapFlight: Promise<void> | null = null;
  private generation = 0;

  constructor(
    private transport: typeof fetch = (...args) => fetch(...args),
    private storage: Storage = sessionStorage,
  ) {}
  snapshot = () => this.session;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  private publish(user: User | null) {
    this.session = { user, ready: true };
    this.listeners.forEach((listener) => listener());
  }
  clear = () => {
    this.generation++;
    this.accessToken = null;
    this.storage.removeItem(refreshKey);
    this.publish(null);
  };
  private accept(tokens: Tokens) {
    const user = decodeUser(tokens.accessToken);
    if (typeof tokens.refreshToken !== "string" || !tokens.refreshToken)
      throw new Error("Resposta de autenticação inválida.");
    this.storage.setItem(refreshKey, tokens.refreshToken);
    this.accessToken = tokens.accessToken;
    this.publish(user);
  }
  private async send(
    path: string,
    options: RequestOptions,
    token: string | null,
  ): Promise<Response> {
    const {
      body,
      public: isPublic,
      keepSessionOnUnauthorized: _keepSession,
      ...init
    } = options;
    const headers = new Headers(init.headers);
    headers.set("Accept", "application/json");
    if (body !== undefined) headers.set("Content-Type", "application/json");
    if (!isPublic && token) headers.set("Authorization", `Bearer ${token}`);
    try {
      return await this.transport(path, {
        ...init,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (error) {
      if (init.signal?.aborted) throw error;
      throw new NetworkError();
    }
  }
  private async read<T>(response: Response): Promise<T> {
    if (response.status === 204) return undefined as T;
    let text: string;
    try {
      text = await response.text();
    } catch {
      throw new NetworkError();
    }
    let data: Record<string, unknown> | null = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      /* Gateway errors may not be JSON. */
    }
    if (!response.ok) {
      const fields: Record<string, string> = {};
      if (data?.fieldErrors && typeof data.fieldErrors === "object") {
        for (const [key, value] of Object.entries(data.fieldErrors))
          if (typeof value === "string") fields[key] = value;
      }
      throw new ApiError(
        response.status,
        typeof data?.message === "string"
          ? data.message
          : `Falha na solicitação (${response.status}).`,
        fields,
        response.headers.get("Retry-After"),
      );
    }
    if (data === null) throw new NetworkError();
    return data as T;
  }
  refresh(): Promise<void> {
    if (this.refreshFlight) return this.refreshFlight;
    const generation = this.generation;
    this.refreshFlight = Promise.resolve().then(async () => {
      try {
        const refreshToken = this.storage.getItem(refreshKey);
        if (!refreshToken)
          throw new ApiError(401, "Sua sessão expirou. Entre novamente.");
        const tokens = await this.read<Tokens>(
          await this.send(
            "/auth/refresh",
            { method: "POST", public: true, body: { refreshToken } },
            null,
          ),
        );
        if (generation !== this.generation)
          throw new ApiError(401, "Sessão encerrada.");
        this.accept(tokens);
      } catch (error) {
        if (generation === this.generation) this.clear();
        throw error;
      } finally {
        this.refreshFlight = null;
      }
    });
    return this.refreshFlight;
  }
  bootstrap = (): Promise<void> => {
    if (!this.bootstrapFlight) {
      this.bootstrapFlight = this.storage.getItem(refreshKey)
        ? this.refresh().catch(() => undefined)
        : Promise.resolve().then(() => this.publish(null));
    }
    return this.bootstrapFlight;
  };
  async login(email: string, password: string) {
    const generation = ++this.generation;
    const tokens = await this.request<Tokens>("/auth/login", {
      method: "POST",
      public: true,
      body: { email, password },
    });
    if (generation === this.generation) this.accept(tokens);
  }
  async logout() {
    // Finish rotation before revoking so the newest token is sent to logout.
    await this.refreshFlight?.catch(() => undefined);
    const refreshToken = this.storage.getItem(refreshKey);
    this.clear();
    if (refreshToken)
      await this.request<void>("/auth/logout", {
        method: "POST",
        public: true,
        body: { refreshToken },
      });
  }
  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const generation = this.generation;
    if (!options.public && !this.accessToken) await this.refresh();
    options.signal?.throwIfAborted();
    const sentToken = this.accessToken;
    let response = await this.send(path, options, sentToken);
    if (!options.public && response.status === 401) {
      if (generation !== this.generation)
        throw new ApiError(401, "Sessão encerrada.");
      // A delayed 401 for the previous access token can reuse the already rotated token.
      if (sentToken === this.accessToken) await this.refresh();
      if (generation !== this.generation)
        throw new ApiError(401, "Sessão encerrada.");
      options.signal?.throwIfAborted();
      response = await this.send(path, options, this.accessToken);
      if (generation !== this.generation)
        throw new ApiError(401, "Sessão encerrada.");
      if (response.status === 401 && !options.keepSessionOnUnauthorized)
        this.clear();
    }
    return this.read<T>(response);
  }
}
export const api = new ApiClient();
