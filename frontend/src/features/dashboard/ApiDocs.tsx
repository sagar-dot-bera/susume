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
      desc: 'Streams interaction events used to build weighted user vector profiles. Supported types: VIEW, CLICK, LIKE, PURCHASE.',
      headers: {
        'Content-Type': 'application/json',
        'X-API-KEY': activeKey
      },
      code: {
        curl: `curl -X POST http://localhost:8080/api/v1/interactions \\
  -H "Content-Type: application/json" \\
  -H "X-API-KEY: ${activeKey}" \\
  -d '{
    "externalUserId": "user-8812",
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
    externalUserId: 'user-8812',
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
    "externalUserId": "user-8812",
    "externalItemId": "manga-905",
    "interactionType": "LIKE",
    "timestamp": datetime.now(timezone.utc).isoformat()
}

res = requests.post(url, headers=headers, json=payload)
print("Status:", res.status_code)`
      },
      response: `200 OK`
    },
    {
      title: '3. Get Vector Recommendations',
      method: 'GET',
      path: '/api/v1/recommendations',
      desc: 'Retrieves cosine-similarity recommendation items for a user. Automatically falls back to trending strategy if cold-start user.',
      headers: {
        'X-API-KEY': activeKey
      },
      code: {
        curl: `curl -X GET "http://localhost:8080/api/v1/recommendations" \\
  -H "Content-Type: application/json" \\
  -H "X-API-KEY: ${activeKey}" \\
  -d '{
    "externalUserId": "user-8812",
    "limit": 5
  }'`,
        javascript: `fetch('http://localhost:8080/api/v1/recommendations', {
  method: 'GET',
  headers: {
    'Content-Type': 'application/json',
    'X-API-KEY': '${activeKey}'
  },
  body: JSON.stringify({
    externalUserId: 'user-8812',
    limit: 5
  })
})
.then(res => res.json())
.then(data => console.log('Recommendations list:', data));`,
        python: `import requests

url = "http://localhost:8080/api/v1/recommendations"
headers = {
    "Content-Type": "application/json",
    "X-API-KEY": "${activeKey}"
}
payload = {
    "externalUserId": "user-8812",
    "limit": 5
}

res = requests.get(url, headers=headers, json=payload)
print(res.json())`
      },
      response: `{
  "userId": "d748f219-[#...]",
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
}`
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
        </div>

      </div>
    </div>
  );
};
