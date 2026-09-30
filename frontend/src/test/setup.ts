import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Use browser storage from jsdom, including on Node versions with native Web Storage.
const browser = (globalThis as unknown as { jsdom: { window: Window } }).jsdom.window;
vi.stubGlobal('localStorage', browser.localStorage);
vi.stubGlobal('sessionStorage', browser.sessionStorage);

afterEach(cleanup);
