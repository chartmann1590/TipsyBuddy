/**
 * TipsyBuddy Feedback Worker — GitHub-backed in-app bug/issue reporter proxy.
 *
 * Security model:
 *   Android app (untrusted) --HTTPS/JSON--> this Worker --GitHub REST API--> GitHub repo
 *   The GitHub PAT lives ONLY as the Worker secret `GITHUB_TOKEN` (env.GITHUB_TOKEN).
 *   It is never shipped to Android, never logged, never returned to clients.
 *
 * Exposed routes (narrow allow-list, NOT a generic GitHub proxy):
 *   GET  /health
 *   POST /api/issues
 *   GET  /api/issues/:number
 *   GET  /api/issues/:number/comments
 *   POST /api/issues/:number/comments
 *   POST /api/assets
 */

export interface Env {
  /** Cloudflare Worker secret — set via `wrangler secret put GITHUB_TOKEN`. Never commit. */
  GITHUB_TOKEN: string;
  /** Non-secret configuration (wrangler.jsonc vars). */
  GITHUB_REPO_OWNER?: string;
  GITHUB_REPO_NAME?: string;
  FEEDBACK_ASSETS_DIR?: string;
}

const GITHUB_API_BASE = "https://api.github.com";
const WORKER_USER_AGENT = "TipsyBuddy-Feedback-Worker";

// Input limits.
const MAX_TITLE_LEN = 200;
const MAX_ISSUE_BODY_BYTES = 50 * 1024;
const MAX_COMMENT_BODY_BYTES = 25 * 1024;
const MAX_BASE64_LEN = 11_000_000; // ~8MB binary
const MAX_REQUEST_BYTES = 12 * 1024 * 1024;

// Simple in-memory per-instance rate limiting (best-effort; use Cloudflare
// Rate Limiting rules in production for stronger guarantees).
const rateBuckets = new Map<string, { count: number; resetAt: number }>();
const RATE_WINDOW_MS = 60_000;
const RATE_MAX_REQUESTS = 60;

const ALLOWED_IMAGE_EXTS = new Set(["png", "jpg", "jpeg", "webp"]);

function configuredRepo(env: Env): { owner: string; repo: string; assetsDir: string } {
  return {
    owner: (env.GITHUB_REPO_OWNER || "chartmann1590").trim(),
    repo: (env.GITHUB_REPO_NAME || "TipsyBuddy").trim(),
    assetsDir: (env.FEEDBACK_ASSETS_DIR || "feedback-assets").trim().replace(/^\/+|\/+$/g, "") || "feedback-assets",
  };
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

function safeError(message: string, status: number): Response {
  return json({ error: message }, status);
}

function getClientIp(request: Request): string {
  return (
    request.headers.get("CF-Connecting-IP") ||
    request.headers.get("X-Forwarded-For")?.split(",")[0]?.trim() ||
    "unknown"
  );
}

function checkRateLimit(request: Request): boolean {
  const key = getClientIp(request);
  const now = Date.now();
  const bucket = rateBuckets.get(key);
  if (!bucket || now >= bucket.resetAt) {
    rateBuckets.set(key, { count: 1, resetAt: now + RATE_WINDOW_MS });
    return true;
  }
  bucket.count += 1;
  return bucket.count <= RATE_MAX_REQUESTS;
}

async function githubRequest(env: Env, path: string, init?: RequestInit): Promise<Response> {
  const headers = new Headers(init?.headers);
  headers.set("Accept", "application/vnd.github+json");
  headers.set("X-GitHub-Api-Version", "2022-11-28");
  // Bearer token lives only in the Worker environment. Never log this header.
  headers.set("Authorization", `Bearer ${env.GITHUB_TOKEN}`);
  headers.set("User-Agent", WORKER_USER_AGENT);
  if (init?.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  return fetch(`${GITHUB_API_BASE}${path}`, { ...init, headers });
}

async function githubErrorMessage(res: Response, fallback: string): Promise<string> {
  // Map GitHub failures to safe client messages; never include tokens/headers.
  if (res.status === 401 || res.status === 403) {
    let hint = "";
    try {
      const body = (await res.clone().json()) as { message?: string };
      if (body?.message) hint = ` (${body.message.slice(0, 160)})`;
    } catch {
      // ignore parse errors
    }
    return `${fallback} GitHub rejected the request (HTTP ${res.status})${hint}. Check GITHUB_TOKEN permissions (Metadata: Read, Issues: Read and write, Contents: Read and write).`;
  }
  if (res.status === 404) return `${fallback} Resource not found on GitHub.`;
  if (res.status === 422) {
    try {
      const body = (await res.clone().json()) as { message?: string };
      if (body?.message) return `${fallback} ${body.message.slice(0, 200)}`;
    } catch {
      // ignore
    }
  }
  return `${fallback} GitHub API error (HTTP ${res.status}).`;
}

function parseIssueNumber(raw: string | null): number | null {
  if (!raw) return null;
  if (!/^\d+$/.test(raw)) return null;
  const n = Number(raw);
  if (!Number.isSafeInteger(n) || n < 1 || n > 2_000_000_000) return null;
  return n;
}

function sanitizeFileName(raw: string): { name: string | null; ext: string | null } {
  // Strip traversal, separators, and unsafe chars; keep a safe image extension.
  const base = raw.split(/[\\/]/).pop() ?? "";
  const cleaned = base
    .replace(/\.\.+/g, "")
    .replace(/[^a-zA-Z0-9._-]/g, "_")
    .replace(/^_+/, "")
    .slice(0, 100);
  if (!cleaned) return { name: null, ext: null };
  const dot = cleaned.lastIndexOf(".");
  if (dot <= 0 || dot === cleaned.length - 1) return { name: null, ext: null };
  const ext = cleaned.slice(dot + 1).toLowerCase();
  if (!ALLOWED_IMAGE_EXTS.has(ext)) return { name: null, ext: null };
  const stem = cleaned.slice(0, dot).replace(/\.+$/g, "").slice(0, 80) || "image";
  return { name: `${stem}.${ext}`, ext };
}

interface GitHubIssue {
  number: number;
  title: string;
  state: string;
  html_url: string;
  created_at: string;
  body?: string | null;
}

function normalizeIssue(issue: GitHubIssue) {
  return {
    number: issue.number,
    title: issue.title,
    state: issue.state,
    htmlUrl: issue.html_url,
    createdAt: issue.created_at,
    ...(issue.body !== undefined ? { body: issue.body ?? "" } : {}),
  };
}

interface GitHubComment {
  id: number;
  body?: string | null;
  created_at: string;
  user?: { login?: string } | null;
}

function normalizeComment(comment: GitHubComment) {
  return {
    id: comment.id,
    body: comment.body ?? "",
    createdAt: comment.created_at,
    user: { login: comment.user?.login ?? "unknown" },
  };
}

function requireJsonContent(request: Request): Response | null {
  const ct = request.headers.get("Content-Type") || "";
  if (!ct.toLowerCase().includes("application/json")) {
    return safeError("Content-Type must be application/json.", 400);
  }
  return null;
}

async function readJsonBody<T>(request: Request): Promise<{ value: T | null; error: Response | null }> {
  const text = await request.text();
  if (text.length > MAX_REQUEST_BYTES) {
    return { value: null, error: safeError("Request body too large.", 413) };
  }
  if (!text) return { value: {} as T, error: null };
  try {
    return { value: JSON.parse(text) as T, error: null };
  } catch {
    return { value: null, error: safeError("Malformed JSON body.", 400) };
  }
}

function isValidBase64(s: string): boolean {
  if (s.length === 0 || s.length % 4 !== 0) return false;
  return /^[A-Za-z0-9+/]*={0,2}$/.test(s);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "") || "/";
    const method = request.method.toUpperCase();

    if (!checkRateLimit(request)) {
      return safeError("Too many requests. Please try again later.", 429);
    }

    // --- Health -----------------------------------------------------------------
    if (path === "/health") {
      if (method !== "GET") return safeError("Method not allowed.", 405);
      const repo = configuredRepo(env);
      return json({
        ok: true,
        service: "feedback-api",
        githubRepositoryConfigured: Boolean(env.GITHUB_TOKEN && repo.owner && repo.repo),
      });
    }

    // --- POST /api/issues (create) ----------------------------------------------
    if (path === "/api/issues" && method === "POST") {
      if (!env.GITHUB_TOKEN) return safeError("Feedback service is not configured.", 500);
      const ctError = requireJsonContent(request);
      if (ctError) return ctError;
      const { value: body, error } = await readJsonBody<{ title?: unknown; body?: unknown }>(request);
      if (error) return error;
      const title = typeof body?.title === "string" ? body.title.trim() : "";
      const issueBody = typeof body?.body === "string" ? body.body : "";
      if (!title) return safeError("Issue title is required.", 400);
      if (title.length > MAX_TITLE_LEN) return safeError(`Issue title must be at most ${MAX_TITLE_LEN} characters.`, 400);
      if (new TextEncoder().encode(issueBody).length > MAX_ISSUE_BODY_BYTES) {
        return safeError("Issue body is too large.", 413);
      }
      const repo = configuredRepo(env);
      let gh: Response;
      try {
        gh = await githubRequest(env, `/repos/${repo.owner}/${repo.repo}/issues`, {
          method: "POST",
          body: JSON.stringify({ title, body: issueBody }),
        });
      } catch {
        return safeError("Unable to create issue.", 502);
      }
      if (!gh.ok) return safeError(await githubErrorMessage(gh, "Unable to create issue."), 502);
      const created = (await gh.json()) as GitHubIssue;
      return json(normalizeIssue(created), 201);
    }

    // --- Issue number routes ------------------------------------------------------
    const issueMatch = path.match(/^\/api\/issues\/([^/]+)(\/comments)?$/);
    if (issueMatch) {
      const number = parseIssueNumber(issueMatch[1]);
      const isComments = Boolean(issueMatch[2]);
      if (number === null) return safeError("Invalid issue number.", 400);
      if (!env.GITHUB_TOKEN) return safeError("Feedback service is not configured.", 500);
      const repo = configuredRepo(env);

      if (!isComments && method === "GET") {
        let gh: Response;
        try {
          gh = await githubRequest(env, `/repos/${repo.owner}/${repo.repo}/issues/${number}`);
        } catch {
          return safeError("Unable to fetch issue.", 502);
        }
        if (gh.status === 404) return safeError("Issue not found.", 404);
        if (!gh.ok) return safeError(await githubErrorMessage(gh, "Unable to fetch issue."), 502);
        return json(normalizeIssue((await gh.json()) as GitHubIssue));
      }

      if (isComments && method === "GET") {
        let gh: Response;
        try {
          gh = await githubRequest(
            env,
            `/repos/${repo.owner}/${repo.repo}/issues/${number}/comments?per_page=100`,
          );
        } catch {
          return safeError("Unable to fetch comments.", 502);
        }
        if (gh.status === 404) return safeError("Issue not found.", 404);
        if (!gh.ok) return safeError(await githubErrorMessage(gh, "Unable to fetch comments."), 502);
        const comments = (await gh.json()) as GitHubComment[];
        comments.sort((a, b) => new Date(a.created_at).getTime() - new Date(b.created_at).getTime());
        return json(comments.map(normalizeComment));
      }

      if (isComments && method === "POST") {
        const ctError = requireJsonContent(request);
        if (ctError) return ctError;
        const { value: payload, error } = await readJsonBody<{ body?: unknown }>(request);
        if (error) return error;
        const commentBody = typeof payload?.body === "string" ? payload.body : "";
        if (!commentBody.trim()) return safeError("Comment body is required.", 400);
        if (new TextEncoder().encode(commentBody).length > MAX_COMMENT_BODY_BYTES) {
          return safeError("Comment is too large.", 413);
        }
        let gh: Response;
        try {
          gh = await githubRequest(env, `/repos/${repo.owner}/${repo.repo}/issues/${number}/comments`, {
            method: "POST",
            body: JSON.stringify({ body: commentBody }),
          });
        } catch {
          return safeError("Unable to post comment.", 502);
        }
        if (gh.status === 404) return safeError("Issue not found.", 404);
        if (!gh.ok) return safeError(await githubErrorMessage(gh, "Unable to post comment."), 502);
        return json(normalizeComment((await gh.json()) as GitHubComment), 201);
      }

      return safeError("Method not allowed.", 405);
    }

    // --- POST /api/assets (screenshot upload -> feedback-assets/) ------------------
    if (path === "/api/assets" && method === "POST") {
      if (!env.GITHUB_TOKEN) return safeError("Feedback service is not configured.", 500);
      const ctError = requireJsonContent(request);
      if (ctError) return ctError;
      const { value: payload, error } = await readJsonBody<{ fileName?: unknown; contentBase64?: unknown }>(
        request,
      );
      if (error) return error;
      const rawName = typeof payload?.fileName === "string" ? payload.fileName : "";
      const contentBase64 =
        typeof payload?.contentBase64 === "string" ? payload.contentBase64.replace(/\s+/g, "") : "";
      const { name: safeName } = sanitizeFileName(rawName);
      if (!safeName) {
        return safeError("Invalid filename. Use a .png, .jpg, .jpeg, or .webp filename.", 400);
      }
      if (!contentBase64) return safeError("Image content is required.", 400);
      if (contentBase64.length > MAX_BASE64_LEN) {
        return safeError("Image is too large (max ~8MB).", 413);
      }
      if (!isValidBase64(contentBase64)) return safeError("Image content must be valid Base64.", 400);

      const repo = configuredRepo(env);
      const encodedPath = `${repo.assetsDir}/${safeName}`.split("/").map(encodeURIComponent).join("/");
      let gh: Response;
      try {
        gh = await githubRequest(env, `/repos/${repo.owner}/${repo.repo}/contents/${encodedPath}`, {
          method: "PUT",
          body: JSON.stringify({
            message: `Upload feedback attachment ${safeName}`,
            content: contentBase64,
          }),
        });
      } catch {
        return safeError("Unable to upload attachment.", 502);
      }
      if (!gh.ok) return safeError(await githubErrorMessage(gh, "Unable to upload attachment."), 502);
      const result = (await gh.json()) as {
        content?: { download_url?: string; html_url?: string; path?: string } | null;
      };
      return json(
        {
          downloadUrl: result.content?.download_url ?? null,
          htmlUrl: result.content?.html_url ?? null,
          path: result.content?.path ?? `${repo.assetsDir}/${safeName}`,
        },
        201,
      );
    }
    if (path === "/api/assets") {
      return safeError("Method not allowed.", 405);
    }

    return safeError("Not found.", 404);
  },
};
