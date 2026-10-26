import React, { useState, useEffect } from 'react';
import { store } from '../../mock/store';
import { Card } from '../../components/ui/Card';
import { MascotBubble } from '../../components/ui/MascotBubble';
import {
  Terminal,
  Copy,
  Check,
  Code,
  FileCode
} from 'lucide-react';

const SkeletonBlock: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`bg-brand-primary/10 rounded animate-pulse ${className}`} />
);

type Lang = 'curl' | 'javascript' | 'python';

interface ParamDoc {
  name: string;
  type: string;
  required: boolean;
  description: string;
}

interface ErrorDoc {
  status: string;
  code: string;
  description: string;
}

export const ApiDocs: React.FC = () => {
  const [tenant, setTenant] = useState(store.getCurrentTenant());
  const [activeLang, setActiveLang] = useState<Lang>('curl');
  const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

  useEffect(() => {
    store.fetchDashboardData();
    return store.subscribe(() => {
      setTenant(store.getCurrentTenant());
    });
  }, []);

  const handleCopyCode = (codeText: string, index: number) => {
    navigator.clipboard.writeText(codeText);
    setCopiedIndex(index);
    setTimeout(() => setCopiedIndex(null), 2000);
  };

  if (!tenant) {
    return (
      <div className="space-y-8 animate-fade-in">
        <SkeletonBlock className="w-full h-20 rounded-[8px]" />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div className="lg:col-span-8 space-y-4">
            <SkeletonBlock className="h-12 w-48 rounded-[8px]" />
            {[...Array(3)].map((_, i) => <SkeletonBlock key={i} className="h-64 rounded-[8px]" />)}
          </div>
          <SkeletonBlock className="lg:col-span-4 h-80 rounded-[8px]" />
        </div>
      </div>
    );
  }

  const activeKey = tenant.apiKeys.find(k => k.status === 'ACTIVE')?.key || 'YOUR_API_KEY';

  // Use a UUID-formatted external user ID throughout: the recommendations
  // endpoint requires externalUserId to be a valid UUID, so the same ID must
  // be used when recording interactions for a user you'll later fetch
  // recommendations for.
  const sampleUserId = 'd748f219-2b1c-4a9e-8f21-7a6b3c9d1e05';

  const endpoints = [
    {
      title: '1. Index Catalog Items',
      method: 'POST',
      path: '/api/v1/items',
      desc: 'Creates and vectorizes item metadata using python embedding service. Trigger this when new inventory is registered.',
      headers: {
        'Content-Type': 'application/json',
        'X-API-KEY': activeKey
      },
      code: {
        curl: `curl -X POST http://localhost:8080/api/v1/items \\
  -H "Content-Type: application/json" \\
  -H "X-API-KEY: ${activeKey}" \\
  -d '{
    "externalItemId": "manga-905",
    "metadata": {
      "title": "Jujutsu Kaisen, Vol. 21",
      "category": "shonen",
      "author": "Gege Akutami",
      "description": "The Tokyo No. 1 Colony barrier breaks down as Culling Game players collide."
    }
  }'`,
        javascript: `fetch('http://localhost:8080/api/v1/items', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-API-KEY': '${activeKey}'
  },
  body: JSON.stringify({
    externalItemId: 'manga-905',
    metadata: {
      title: 'Jujutsu Kaisen, Vol. 21',
      category: 'shonen',
      author: 'Gege Akutami',
      description: 'The Tokyo No. 1 Colony barrier breaks down as Culling Game players collide.'
    }
  })
})
.then(res => res.json())
.then(data => console.log('Item created successfully:', data));`,
        python: `import requests

url = "http://localhost:8080/api/v1/items"
headers = {
    "Content-Type": "application/json",
    "X-API-KEY": "${activeKey}"
}
payload = {
    "externalItemId": "manga-905",
    "metadata": {
        "title": "Jujutsu Kaisen, Vol. 21",
        "category": "shonen",
        "author": "Gege Akutami",
        "description": "The Tokyo No. 1 Colony barrier breaks down as Culling Game players collide."
    }
}

res = requests.post(url, headers=headers, json=payload)
print(res.json())`
      },
      response: `{
  "id": "e4a2c076-2f19-4b62-95f2-[#...]",
  "externalItemId": "manga-905",
  "status": "ACTIVE",
  "createdAt": "2026-07-26T13:40:00Z",
  "updatedAt": "2026-07-26T13:40:00Z"
}`
    },
    {
      title: '2. Record User Interaction',
      method: 'POST',
      path: '/api/v1/interactions',
      desc: 'Streams a single clickstream event into the interaction log that powers every downstream recommendation strategy (collaborative filtering, trending decay, personalization). Call this every time a tracked action happens in your product — a page view, a click, a like, an add-to-cart, a share, or a purchase.',
      headers: {
        'Content-Type': 'application/json',
        'X-API-KEY': activeKey
      },
      params: [
        {
          name: 'externalUserId',
          type: 'string',
          required: true,
          description: 'Your own identifier for the user. Use the exact same value every time you record an interaction for this user, and as the externalUserId when fetching their personalized recommendations.'
        },
        {
          name: 'externalItemId',
          type: 'string',
          required: true,
          description: 'Must already be registered via POST /api/v1/items and have status ACTIVE for this tenant, otherwise the request is rejected.'
        },
        {
          name: 'interactionType',
          type: 'string (enum)',
          required: true,
          description: 'One of VIEW, CLICK, LIKE, SHARE, CART, PURCHASE (case-insensitive). Each type carries a different scoring weight: VIEW=1, CLICK=2, LIKE=3, SHARE=4, CART=7, PURCHASE=10 — heavier actions count more toward trending & personalized scores.'
        },
        {
          name: 'timestamp',
          type: 'string (ISO-8601)',
          required: false,
          description: "Defaults to the server's current time if omitted. Pass an explicit timestamp when backfilling historical events."
        }
      ] as ParamDoc[],
      code: {
        curl: `curl -X POST http://localhost:8080/api/v1/interactions \\
  -H "Content-Type: application/json" \\
  -H "X-API-KEY: ${activeKey}" \\
  -d '{
    "externalUserId": "${sampleUserId}",
    "externalItemId": "manga-905",
    "interactionType": "LIKE",
    "timestamp": "2026-07-26T13:40:00Z"
  }'`,
        javascript: `fetch('http://localhost:8080/api/v1/interactions', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-API-KEY': '${activeKey}'
  },
  body: JSON.stringify({
    externalUserId: '${sampleUserId}',
    externalItemId: 'manga-905',
    interactionType: 'LIKE',
    timestamp: new Date().toISOString()
  })
})
.then(res => console.log('Interaction recorded successfully'));`,
        python: `import requests
from datetime import datetime, timezone

url = "http://localhost:8080/api/v1/interactions"
headers = {
    "Content-Type": "application/json",
    "X-API-KEY": "${activeKey}"
}
payload = {
    "externalUserId": "${sampleUserId}",
    "externalItemId": "manga-905",
    "interactionType": "LIKE",
    "timestamp": datetime.now(timezone.utc).isoformat()
}

res = requests.post(url, headers=headers, json=payload)
print("Status:", res.status_code)`
      },
      response: `200 OK`,
      errors: [
        { status: '400', code: 'BAD_REQUEST', description: '"Item not found" — externalItemId does not match any item registered for this tenant.' },
        { status: '400', code: 'BAD_REQUEST', description: '"Item is not active" — the item exists but its status is not ACTIVE (e.g. still embedding, or deactivated).' },
        { status: '400', code: 'VALIDATION_ERROR', description: 'A required field is missing, or interactionType is not one of the supported enum values.' },
        { status: '401', code: 'UNAUTHORIZED', description: 'Missing or invalid X-API-KEY header.' }
      ] as ErrorDoc[],
      notes: [
        'Recording an interaction evicts this user’s cached recommendation set, so their very next GET /api/v1/recommendations call reflects the new signal immediately.',
        'There is no separate "unlike" or delete endpoint — interactions are an append-only event log, matching how the offline training pipeline replays history.'
      ]
    },
    {
      title: '3. Get Personalized Recommendations',
      method: 'GET',
      path: '/api/v1/recommendations',
      desc: 'Retrieves top-K ranked items for a user by blending candidate strategies (collaborative filtering, content-based, personalized, etc.) through the ML re-ranking service. Automatically falls back to the trending strategy for cold-start users with no recorded interactions.',
      headers: {
        'X-API-KEY': activeKey
      },
      params: [
        {
          name: 'externalUserId',
          type: 'query string (UUID)',
          required: true,
          description: `Unlike other endpoints, this must be formatted as a UUID (e.g. ${sampleUserId}). Use the same UUID you passed as externalUserId when recording that user's interactions.`
        },
        {
          name: 'limit',
          type: 'query integer',
          required: false,
          description: 'Number of recommendations to return. Defaults to 10, capped by the tenant’s configured max limit.'
        }
      ] as ParamDoc[],
      code: {
        curl: `curl -X GET "http://localhost:8080/api/v1/recommendations?externalUserId=${sampleUserId}&limit=5" \\
  -H "X-API-KEY: ${activeKey}"`,
        javascript: `fetch('http://localhost:8080/api/v1/recommendations?externalUserId=${sampleUserId}&limit=5', {
  method: 'GET',
  headers: {
    'X-API-KEY': '${activeKey}'
  }
})
.then(res => res.json())
.then(data => console.log('Recommendations list:', data));`,
        python: `import requests

url = "http://localhost:8080/api/v1/recommendations"
headers = {
    "X-API-KEY": "${activeKey}"
}
params = {
    "externalUserId": "${sampleUserId}",
    "limit": 5
}

res = requests.get(url, headers=headers, params=params)
print(res.json())`
      },
      response: `{
  "userId": "${sampleUserId}",
  "strategy": "personalized",
  "recommendations": [
    {
      "externalItemId": "manga-905",
      "similarityScore": 0.892,
      "metadata": { "title": "Jujutsu Kaisen, Vol. 21", "category": "shonen" }
    },
    {
      "externalItemId": "manga-102",
      "similarityScore": 0.741,
      "metadata": { "title": "Chainsaw Man, Vol. 1", "category": "dark fantasy" }
    }
  ]
}`,
      errors: [
        { status: '400', code: 'VALIDATION_ERROR', description: 'externalUserId is missing or is not a valid UUID string.' },
        { status: '401', code: 'UNAUTHORIZED', description: 'Missing or invalid X-API-KEY header.' }
      ] as ErrorDoc[]
    },
    {
      title: '4. Get Trending Recommendations',
      method: 'GET',
      path: '/api/v1/recommendations/trending',
      desc: 'Returns globally trending items for the tenant, ranked by a weighted interaction count over a lookback window. No externalUserId needed — use this for anonymous/logged-out surfaces, and it doubles as the automatic fallback for cold-start users on the personalized endpoint above.',
      headers: {
        'X-API-KEY': activeKey
      },
      params: [
        {
          name: 'limit',
          type: 'integer (query param)',
          required: false,
          description: 'Number of items to return. Defaults to 10.'
        },
        {
          name: 'days',
          type: 'integer (query param)',
          required: false,
          description: 'Lookback window in days used to compute trending scores. Defaults to 30.'
        }
      ] as ParamDoc[],
      code: {
        curl: `curl -X GET "http://localhost:8080/api/v1/recommendations/trending?limit=5&days=7" \\
  -H "X-API-KEY: ${activeKey}"`,
        javascript: `fetch('http://localhost:8080/api/v1/recommendations/trending?limit=5&days=7', {
  method: 'GET',
  headers: {
    'X-API-KEY': '${activeKey}'
  }
})
.then(res => res.json())
.then(data => console.log('Trending items:', data));`,
        python: `import requests

url = "http://localhost:8080/api/v1/recommendations/trending"
headers = {
    "X-API-KEY": "${activeKey}"
}
params = {
    "limit": 5,
    "days": 7
}

res = requests.get(url, headers=headers, params=params)
print(res.json())`
      },
      response: `{
  "userId": null,
  "strategy": "trending",
  "recommendations": [
    {
      "externalItemId": "manga-905",
      "similarityScore": 128.0,
      "metadata": { "title": "Jujutsu Kaisen, Vol. 21", "category": "shonen" }
    },
    {
      "externalItemId": "manga-102",
      "similarityScore": 94.0,
      "metadata": { "title": "Chainsaw Man, Vol. 1", "category": "dark fantasy" }
    }
  ]
}`,
      errors: [
        { status: '400', code: '-', description: 'limit or days is 0 or negative.' },
        { status: '401', code: 'UNAUTHORIZED', description: 'Missing or invalid X-API-KEY header.' }
      ] as ErrorDoc[]
    }
  ];

  const languages = [
    { id: 'curl', name: 'cURL', icon: Terminal },
    { id: 'javascript', name: 'JavaScript', icon: Code },
    { id: 'python', name: 'Python', icon: FileCode }
  ];

  return (
    <div className="space-y-8 animate-fade-in text-left">
      {/* Header Banner */}
      <div className="border-2 border-brand-primary rounded-[8px] p-6 bg-white shadow-hard flex justify-between items-center gap-6">
        <div>
          <h2 className="text-2xl md:text-3xl font-display font-normal text-brand-primary tracking-wide uppercase leading-none">
            Developer Documentation
          </h2>
          <p className="text-xs text-text-secondary font-medium mt-2 font-sans">
            Reference definitions and request syntax configurations for the isolated vector engine.
          </p>
        </div>
        <div className="hidden sm:flex border-2 border-brand-primary rounded-[4px] p-1 font-heading font-extrabold text-[10px] bg-brand-accent/20 text-brand-primary uppercase">
          Spring Boot endpoints
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: API Endpoints Docs (8 cols) */}
        <div className="lg:col-span-8 space-y-8">

          {/* Lang switcher */}
          <div className="flex border-2 border-brand-primary rounded-[8px] bg-white p-1 shadow-hard-sm w-fit font-heading font-extrabold text-xs uppercase tracking-wider">
            {languages.map((l) => {
              const Icon = l.icon;
              return (
                <button
                  key={l.id}
                  onClick={() => setActiveLang(l.id as Lang)}
                  className={`flex items-center gap-1.5 px-4 py-2 rounded-[4px] cursor-pointer select-none transition-colors ${
                    activeLang === l.id
                      ? 'bg-brand-accent text-brand-primary border border-brand-primary shadow-hard-sm'
                      : 'border border-transparent hover:bg-bg-base'
                  }`}
                >
                  <Icon size={14} />
                  <span>{l.name}</span>
                </button>
              );
            })}
          </div>

          {/* Endpoints Loop */}
          {endpoints.map((ep, idx) => {
            const isGet = ep.method === 'GET';
            return (
              <Card
                key={idx}
                title={ep.title}
                subtitle={ep.desc}
              >
                <div className="space-y-4">
                  {/* Method & URL */}
                  <div className="flex items-center gap-3 font-mono text-xs">
                    <span className={`px-2.5 py-1 rounded font-extrabold border ${
                      isGet
                        ? 'bg-brand-accent/20 text-brand-primary border-brand-primary'
                        : 'bg-brand-secondary/20 text-brand-secondary border-brand-secondary'
                    }`}>
                      {ep.method}
                    </span>
                    <span className="font-bold text-brand-primary text-sm bg-bg-base/50 px-2 py-1 border border-brand-primary/20 rounded">
                      {ep.path}
                    </span>
                  </div>

                  {/* Headers Checklist */}
                  <div className="space-y-1">
                    <p className="text-[10px] font-bold text-text-secondary uppercase tracking-wider font-heading">Required Headers</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      {Object.entries(ep.headers).map(([key, val]) => (
                        <div key={key} className="bg-bg-base/30 border-2 border-brand-primary rounded px-2.5 py-1.5 text-[10px] font-mono">
                          <span className="font-extrabold text-brand-primary block">{key}</span>
                          <span className="text-text-secondary truncate block" title={val}>{val}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* Parameter Reference */}
                  {'params' in ep && ep.params && ep.params.length > 0 && (
                    <div className="space-y-1">
                      <p className="text-[10px] font-bold text-text-secondary uppercase tracking-wider font-heading">Parameters</p>
                      <div className="border-2 border-brand-primary rounded-[6px] overflow-hidden divide-y-2 divide-brand-primary">
                        {ep.params.map((p) => (
                          <div key={p.name} className="bg-bg-base/30 px-3 py-2 flex flex-col sm:flex-row sm:items-start gap-1 sm:gap-3 text-xs">
                            <div className="flex items-center gap-1.5 sm:w-40 flex-shrink-0">
                              <span className="font-mono font-extrabold text-brand-primary">{p.name}</span>
                              {p.required ? (
                                <span className="text-[9px] font-heading font-extrabold uppercase text-brand-secondary">required</span>
                              ) : (
                                <span className="text-[9px] font-heading font-extrabold uppercase text-text-secondary/70">optional</span>
                              )}
                            </div>
                            <div className="flex-1 text-text-secondary font-sans">
                              <span className="font-mono text-[10px] bg-white border border-brand-primary/30 rounded px-1 py-0.5 mr-1.5">{p.type}</span>
                              {p.description}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Request Code Block */}
                  <div className="space-y-1">
                    <div className="flex justify-between items-center text-[10px] font-bold text-text-secondary uppercase tracking-wider font-heading">
                      <span>Request Payload Example ({activeLang})</span>
                      <button
                        onClick={() => handleCopyCode(ep.code[activeLang], idx)}
                        className="flex items-center gap-1 hover:text-brand-primary cursor-pointer"
                      >
                        {copiedIndex === idx ? (
                          <>
                            <Check size={12} className="text-brand-secondary" />
                            <span>Copied</span>
                          </>
                        ) : (
                          <>
                            <Copy size={12} />
                            <span>Copy Code</span>
                          </>
                        )}
                      </button>
                    </div>
                    <pre className="bg-[#1a1c30] text-[#f4f3ec] p-4 rounded-[6px] border-2 border-brand-primary text-xs font-mono overflow-x-auto whitespace-pre select-all text-left">
                      {ep.code[activeLang]}
                    </pre>
                  </div>

                  {/* Response Code Block */}
                  <div className="space-y-1">
                    <p className="text-[10px] font-bold text-text-secondary uppercase tracking-wider font-heading">Expected Response</p>
                    <pre className="bg-bg-base/60 text-brand-primary p-4 rounded-[6px] border-2 border-brand-primary text-xs font-mono overflow-x-auto whitespace-pre select-all text-left">
                      {ep.response}
                    </pre>
                  </div>

                  {/* Error Responses */}
                  {'errors' in ep && ep.errors && ep.errors.length > 0 && (
                    <div className="space-y-1">
                      <p className="text-[10px] font-bold text-text-secondary uppercase tracking-wider font-heading">Error Responses</p>
                      <div className="border-2 border-brand-secondary rounded-[6px] overflow-hidden divide-y-2 divide-brand-secondary/40">
                        {ep.errors.map((err, errIdx) => (
                          <div key={errIdx} className="bg-brand-secondary/5 px-3 py-2 flex flex-col sm:flex-row sm:items-start gap-1 sm:gap-3 text-xs">
                            <div className="flex items-center gap-1.5 sm:w-32 flex-shrink-0 font-mono font-extrabold text-brand-secondary">
                              <span>{err.status}</span>
                              <span className="text-[9px] font-heading uppercase truncate" title={err.code}>{err.code}</span>
                            </div>
                            <div className="flex-1 text-text-secondary font-sans">{err.description}</div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Notes */}
                  {'notes' in ep && ep.notes && ep.notes.length > 0 && (
                    <ul className="space-y-1.5 text-xs text-text-secondary font-sans list-disc pl-4">
                      {ep.notes.map((note, noteIdx) => (
                        <li key={noteIdx}>{note}</li>
                      ))}
                    </ul>
                  )}

                </div>
              </Card>
            );
          })}
        </div>

        {/* Right Column: Mascot & guides (4 cols) */}
        <div className="lg:col-span-4 space-y-6">
          <MascotBubble
            mascot="dev"
            bubbleColor="accent"
            message={
              <div className="space-y-1.5 text-xs text-brand-primary">
                <p className="font-bold font-heading uppercase text-xs">Vector Dimensions</p>
                <p>
                  "We use sentence-transformers/all-MiniLM-L6-v2 which maps item text to 384-dimensional dense vectors. Cosine similarity outputs scores from 0.0 to 1.0."
                </p>
              </div>
            }
          />

          <Card title="API Key Scopes" padding="sm" variant="cream">
            <div className="space-y-3 font-sans text-xs">
              <p className="text-text-secondary font-medium">
                Requests to items, interactions, and recommendations must include the <code className="bg-white px-1 py-0.5 rounded font-bold border border-brand-primary">X-API-KEY</code> header.
              </p>
            </div>
          </Card>

          <Card title="Recording Interactions" padding="sm" variant="cream">
            <div className="space-y-3 font-sans text-xs text-text-secondary">
              <p>
                A typical integration calls <code className="bg-white px-1 py-0.5 rounded font-bold border border-brand-primary">POST /interactions</code> on every VIEW, CLICK, LIKE, SHARE, CART, and PURCHASE event, then calls <code className="bg-white px-1 py-0.5 rounded font-bold border border-brand-primary">GET /recommendations</code> to render a personalized feed for that same user.
              </p>
              <p>
                Higher-weight actions (PURCHASE, CART) move the needle on personalized & trending scores much faster than passive VIEW events — so it's fine to fire interaction calls fire-and-forget from the client without blocking the UI.
              </p>
            </div>
          </Card>
        </div>

      </div>
    </div>
  );
};
