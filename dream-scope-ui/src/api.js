async function fail(res) {
  let detail = ''
  try {
    const body = await res.json()
    detail = body.message || body.error || ''
  } catch {
    detail = ''
  }
  const error = new Error(detail || `${res.status} ${res.statusText}`)
  error.status = res.status
  throw error
}

async function send(path, options) {
  const res = await fetch(path, options)
  if (!res.ok) {
    await fail(res)
  }
  if (res.status === 204) {
    return null
  }
  const text = await res.text()
  return text ? JSON.parse(text) : null
}

export function invokeAgent(body) {
  return send('/api/agents/invoke', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export async function streamAgent(body, onEvent, signal) {
  const res = await fetch('/api/agents/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
    },
    body: JSON.stringify(body),
    signal,
  })
  if (!res.ok) {
    await fail(res)
  }
  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    buffer = takeEvents(buffer, onEvent)
  }
  if (buffer.trim()) {
    takeEvents(buffer + '\n\n', onEvent)
  }
}

function takeEvents(buffer, onEvent) {
  const blocks = buffer.split('\n\n')
  const rest = blocks.pop()
  for (const block of blocks) {
    if (!block.trim()) {
      continue
    }
    let name = 'message'
    const data = []
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        name = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        data.push(line.slice(5).trim())
      }
    }
    if (!data.length) {
      continue
    }
    const raw = data.join('\n')
    try {
      onEvent(name, JSON.parse(raw))
    } catch {
      onEvent(name, { text: raw })
    }
  }
  return rest
}

export function addKnowledgeText(body) {
  return send('/api/knowledge/texts', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function addKnowledgeFile(file, fields) {
  const form = new FormData()
  form.append('file', file)
  if (fields.id) {
    form.append('id', fields.id)
  }
  if (fields.source) {
    form.append('source', fields.source)
  }
  if (fields.docType) {
    form.append('docType', fields.docType)
  }
  return send('/api/knowledge/files', { method: 'POST', body: form })
}

export function knowledgeJob(jobId) {
  return send(`/api/knowledge/jobs/${encodeURIComponent(jobId)}`)
}

export function retrieveKnowledge(body) {
  return send('/api/knowledge/retrieve', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function knowledgeSources() {
  return send('/api/knowledge/sources')
}

export function deleteKnowledgeSource(source) {
  const query = new URLSearchParams({ source })
  return send(`/api/knowledge/sources?${query}`, { method: 'DELETE' })
}

export function nacosStatus() {
  return send('/api/nacos/status')
}

export function nacosInstances(serviceId) {
  return send(`/api/nacos/instances/${encodeURIComponent(serviceId)}`)
}

export function agentCard() {
  return send('/.well-known/agent-card.json')
}

export function a2aSend(text) {
  const body = {
    jsonrpc: '2.0',
    id: '1',
    method: 'message/send',
    params: {
      message: {
        kind: 'message',
        messageId: crypto.randomUUID(),
        role: 'user',
        parts: [{ kind: 'text', text }],
      },
    },
  }
  return send('/a2a', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}

export function a2aText(response) {
  const result = response && response.result
  if (!result || typeof result !== 'object') {
    return ''
  }
  const direct = firstText(result.parts)
  if (direct) {
    return direct
  }
  const artifacts = result.artifacts
  if (!Array.isArray(artifacts) || !artifacts.length) {
    return ''
  }
  return firstText(artifacts[0] && artifacts[0].parts)
}

function firstText(parts) {
  if (!Array.isArray(parts)) {
    return ''
  }
  for (const part of parts) {
    if (part && part.text) {
      return String(part.text)
    }
  }
  return ''
}
