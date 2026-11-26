export interface ProblemDetailError {
  status: number;
  title?: string;
  detail?: string;
  errorCode?: string;
  requestId?: string;
  fieldErrors?: Record<string, string>;
}

export class ApiError extends Error {
  status: number;
  errorCode?: string;
  fieldErrors?: Record<string, string>;

  constructor(problem: ProblemDetailError) {
    super(problem.detail || problem.title || `Request failed (${problem.status})`);
    this.status = problem.status;
    this.errorCode = problem.errorCode;
    this.fieldErrors = problem.fieldErrors;
  }
}

let cachedCsrfToken: string | null = null;

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie
    .split("; ")
    .find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.split("=").slice(1).join("=")) : null;
}

export async function ensureCsrfToken(): Promise<string> {
  const fromCookie = readCookie("XSRF-TOKEN");
  if (fromCookie) {
    cachedCsrfToken = fromCookie;
    return fromCookie;
  }
  if (cachedCsrfToken) {
    return cachedCsrfToken;
  }
  const res = await fetch("/api/v1/auth/csrf", { credentials: "include" });
  if (res.ok) {
    const data = (await res.json()) as { token?: string };
    if (data.token) {
      cachedCsrfToken = data.token;
      return data.token;
    }
  }
  return "";
}

export function clearCsrfCache() {
  cachedCsrfToken = null;
}

export async function apiFetch<T>(
  path: string,
  options: RequestInit & { idempotencyKey?: string } = {}
): Promise<T> {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers);

  if (!headers.has("Content-Type") && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }
  if (options.idempotencyKey) {
    headers.set("Idempotency-Key", options.idempotencyKey);
  }

  const isMutating = ["POST", "PUT", "PATCH", "DELETE"].includes(method);
  const isExempt =
    path === "/api/v1/auth/login" ||
    path === "/api/v1/auth/register" ||
    path === "/api/v1/demo/start";

  if (isMutating && !isExempt) {
    const token = await ensureCsrfToken();
    if (token) {
      headers.set("X-XSRF-TOKEN", token);
    }
  }

  const res = await fetch(path, {
    ...options,
    method,
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    let problem: ProblemDetailError = { status: res.status };
    try {
      const parsed = (await res.json()) as ProblemDetailError;
      problem = { ...parsed, status: res.status };
    } catch {
      problem.detail = res.statusText || "Request failed";
    }
    throw new ApiError(problem);
  }

  if (res.status === 204) {
    return undefined as T;
  }

  const contentType = res.headers.get("content-type") || "";
  if (contentType.includes("application/json")) {
    return (await res.json()) as T;
  }
  return (await res.text()) as unknown as T;
}
