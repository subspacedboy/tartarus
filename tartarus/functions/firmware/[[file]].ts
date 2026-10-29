interface R2HttpMetadata {
  contentType?: string;
  contentLanguage?: string;
  contentDisposition?: string;
  contentEncoding?: string;
  cacheControl?: string;
  cacheExpiry?: Date;
}

interface R2ObjectBody {
  body: ReadableStream;
  httpEtag: string;
  range?: { offset: number; length: number };
  writeHttpMetadata(headers: Headers): void;
}

interface R2GetOptions {
  range?: Headers | { offset?: number; length?: number };
  onlyIf?: Headers;
}

interface R2Bucket {
  get(key: string, options?: R2GetOptions): Promise<R2ObjectBody | null>;
}

interface Env {
  FIRMWARE_BUCKET: R2Bucket;
}

interface EventContext {
  request: Request;
  env: Env;
  params: {
    file?: string | string[];
  };
}

export async function onRequest(context: EventContext): Promise<Response> {
  const method = context.request.method;
  if (method !== 'GET' && method !== 'HEAD') {
    return new Response('Method Not Allowed', { status: 405 });
  }

  const bucket = context.env.FIRMWARE_BUCKET;
  if (!bucket) {
    return new Response('FIRMWARE_BUCKET binding is not configured in Cloudflare Pages', { status: 500 });
  }

  const rawParam = context.params.file;
  let filename = '';
  if (Array.isArray(rawParam)) {
    filename = rawParam.join('/');
  } else if (typeof rawParam === 'string') {
    filename = rawParam;
  }

  // Strip leading and trailing slashes
  filename = filename.replace(/^\/+/, '').replace(/\/+$/, '');
  if (!filename || filename.includes('..')) {
    return new Response('File name required', { status: 400 });
  }

  // 1. Try direct filename key (e.g. "esp32.bin" or "release/v1.0.bin")
  let object = await bucket.get(filename, {
    range: context.request.headers,
    onlyIf: context.request.headers,
  });

  // 2. Fallback: try with "firmware/" prefix in case bucket uses a firmware/ subfolder
  if (!object && !filename.startsWith('firmware/')) {
    object = await bucket.get(`firmware/${filename}`, {
      range: context.request.headers,
      onlyIf: context.request.headers,
    });
  }

  if (!object) {
    return new Response('Firmware not found', { status: 404 });
  }

  const headers = new Headers();
  object.writeHttpMetadata(headers);
  headers.set('etag', object.httpEtag);
  headers.set('Cache-Control', 'public, max-age=3600, stale-while-revalidate=86400');
  headers.set('Access-Control-Allow-Origin', '*');

  // If content-type wasn't stored in R2 metadata, infer common firmware types
  if (!headers.has('Content-Type')) {
    if (filename.endsWith('.bin')) {
      headers.set('Content-Type', 'application/octet-stream');
    } else if (filename.endsWith('.json')) {
      headers.set('Content-Type', 'application/json');
    }
  }

  // Handle R2 byte range response (status 206 vs 200)
  const status = object.range ? 206 : 200;

  if (method === 'HEAD') {
    return new Response(null, { headers, status });
  }

  return new Response(object.body, { headers, status });
}
