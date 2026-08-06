import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { store } from '../../mock/store';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { 
  TrendingUp, 
  Activity, 
  Key, 
  Clock, 
  Terminal, 
  Trash2, 
  ArrowRight
} from 'lucide-react';

const SkeletonBlock: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`bg-brand-primary/10 rounded animate-pulse ${className}`} />
);

export const DashboardHome: React.FC = () => {
  const navigate = useNavigate();
  const [tenant, setTenant] = useState(store.getCurrentTenant());
  const [stats, setStats] = useState(store.getStats());
  const [logs, setLogs] = useState(store.getLogs(10));
  const [activeTab, setActiveTab] = useState<'console' | 'details'>('console');

  useEffect(() => {
    store.fetchDashboardData();
    return store.subscribe(() => {
      setTenant(store.getCurrentTenant());
      setStats(store.getStats());
      setLogs(store.getLogs(10));
    });
  }, []);

  const handleClearLogs = () => {
    store.clearLogs();
  };

  if (!tenant) {
    return (
      <div className="space-y-8 animate-fade-in">
        <SkeletonBlock className="w-full h-24 rounded-[8px]" />
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {[...Array(4)].map((_, i) => <SkeletonBlock key={i} className="h-28 rounded-[8px]" />)}
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <SkeletonBlock className="lg:col-span-8 h-80 rounded-[8px]" />
          <SkeletonBlock className="lg:col-span-4 h-80 rounded-[8px]" />
        </div>
      </div>
    );
  }

  const statCards = [
    {
      title: 'Total Recommendations',
      value: stats.totalRecs.toLocaleString(),
      desc: 'Mock queries processed',
      icon: TrendingUp,
      bgColor: 'bg-brand-accent/15 border-brand-primary'
    },
    {
      title: 'Interaction Events',
      value: (stats.totalInteractions ?? 0).toLocaleString(),
      desc: 'Views, clicks & conversions',
      icon: Activity,
      bgColor: 'bg-brand-secondary/10 border-brand-primary'
    },
    {
      title: 'Average Latency',
      value: `${stats.avgLatency}ms`,
      desc: 'Cosine similarity calculations',
      icon: Clock,
      bgColor: 'bg-white border-brand-primary'
    },
    {
      title: 'Active API Keys',
      value: stats.apiKeyCount,
      desc: 'Authorized SDK credentials',
      icon: Key,
      bgColor: 'bg-white border-brand-primary'
    }
  ];

  return (
    <div className="space-y-8 animate-fade-in text-left">
      {/* Editorial Title banner */}
      <div className="border-2 border-brand-primary rounded-[8px] p-6 bg-white shadow-hard flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h2 className="text-2xl md:text-3xl font-display font-normal text-brand-primary tracking-wide uppercase leading-none">
            {tenant.name} Overview
          </h2>
          <p className="text-xs text-text-secondary font-medium mt-2 font-sans">
            Created on {new Date(tenant.createdAt).toLocaleDateString()} • Isolation Namespace: <code className="bg-surface-alt px-1.5 py-0.5 rounded text-brand-primary font-bold">{tenant.slug}</code>
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => navigate('/dashboard/keys')}>
            Manage Keys
          </Button>
          <Button variant="primary" size="sm" onClick={() => navigate('/dashboard/docs')}>
            Interactive API Docs <ArrowRight size={14} />
          </Button>
        </div>
      </div>

      {/* Grid containing Stats Widgets */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {statCards.map((stat, index) => {
          const Icon = stat.icon;
          return (
            <div 
              key={index}
              className={`border-2 border-brand-primary rounded-[8px] p-5 shadow-hard-sm flex justify-between items-start bg-white`}
            >
              <div className="space-y-2">
                <p className="text-[10px] font-extrabold text-text-secondary uppercase tracking-wider font-heading">{stat.title}</p>
                <h3 className="text-3xl font-display font-normal text-brand-primary leading-none tracking-wide">{stat.value}</h3>
                <p className="text-[10px] font-semibold text-text-muted font-sans leading-none">{stat.desc}</p>
              </div>
              <div className="p-2 border-2 border-brand-primary bg-bg-base rounded-[4px]">
                <Icon size={16} className="text-brand-primary" />
              </div>
            </div>
          );
        })}
      </div>

      {/* Editorial 8/4 layout split */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Left Column: API Logs Stream (8 cols) */}
        <div className="lg:col-span-8 space-y-6">
          <Card 
            title="Real-time Sandbox Traffic" 
            subtitle="Live logs stream simulated by background worker. Verify endpoint parameters."
            headerAction={
              <div className="flex items-center gap-2">
                <Button 
                  variant="outline" 
                  size="sm" 
                  onClick={handleClearLogs} 
                  className="py-1 px-2.5 text-xs flex items-center gap-1.5"
                >
                  <Trash2 size={12} /> Clear Logs
                </Button>
              </div>
            }
          >
            <div className="flex border-b-2 border-brand-primary mb-4 text-xs font-bold font-heading uppercase">
              <button 
                onClick={() => setActiveTab('console')}
                className={`px-4 py-2 border-r-2 border-brand-primary cursor-pointer select-none ${activeTab === 'console' ? 'bg-brand-accent/20 text-brand-primary' : 'bg-white hover:bg-bg-base/30'}`}
              >
                Traffic Console
              </button>
              <button 
                onClick={() => setActiveTab('details')}
                className={`px-4 py-2 border-r-2 border-brand-primary cursor-pointer select-none ${activeTab === 'details' ? 'bg-brand-accent/20 text-brand-primary' : 'bg-white hover:bg-bg-base/30'}`}
              >
                Isolation Details
              </button>
            </div>

            {activeTab === 'console' ? (
              <div className="space-y-3 font-mono text-left">
                {logs.length === 0 ? (
                  <div className="py-12 text-center text-xs text-text-secondary border-2 border-dashed border-brand-primary/20 rounded bg-bg-base/20">
                    <Terminal className="mx-auto text-text-muted mb-2 animate-pulse" size={24} />
                    <p className="font-sans font-semibold">Console empty. Live traffic simulations trigger every 8s.</p>
                  </div>
                ) : (
                  <div className="space-y-3 max-h-[360px] overflow-y-auto pr-1">
                    {logs.map((log) => {
                      const isPost = log.method === 'POST';
                      const isError = log.status >= 400;
                      return (
                        <div 
                          key={log.id} 
                          className={`p-3 border-2 border-brand-primary rounded bg-[#1a1c30] text-[#f4f3ec] text-xs shadow-hard-sm`}
                        >
                          <div className="flex flex-wrap items-center justify-between gap-2 border-b border-brand-primary/30 pb-2 mb-2">
                            <div className="flex items-center gap-2">
                              <span className={`px-2 py-0.5 rounded font-extrabold text-[9px] border ${
                                isPost 
                                  ? 'bg-[#E63963]/25 text-[#E63963] border-[#E63963]' 
                                  : 'bg-[#F2C94C]/25 text-[#F2C94C] border-[#F2C94C]'
                              }`}>
                                {log.method}
                              </span>
                              <span className="font-bold text-gray-300">{log.endpoint}</span>
                            </div>
                            <div className="flex items-center gap-3 text-[10px] text-gray-400">
                              <span>{new Date(log.timestamp).toLocaleTimeString()}</span>
                              <span className={`font-extrabold px-1.5 py-0.5 rounded ${
                                isError ? 'bg-red-500/20 text-red-400' : 'bg-emerald-500/20 text-emerald-400'
                              }`}>
                                Status: {log.status}
                              </span>
                              <span className="bg-brand-primary/60 px-1.5 py-0.5 rounded text-white">{log.latencyMs}ms</span>
                            </div>
                          </div>
                          <div className="text-[10px] text-gray-300 bg-black/30 p-2 rounded overflow-x-auto whitespace-pre-wrap select-all">
                            {JSON.stringify(JSON.parse(log.payload), null, 2)}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            ) : (
              <div className="text-sm font-sans space-y-4">
                <p className="text-text-secondary leading-relaxed">
                  Susume isolates storage using multi-tenant router parameters. A Spring Boot filter verifies credentials via incoming headers and binds metadata context to execution threads.
                </p>
                <div className="p-4 border-2 border-brand-primary rounded bg-bg-base">
                  <h4 className="font-heading font-bold uppercase text-xs text-brand-primary mb-2">Workspace Isolation Checklist</h4>
                  <ul className="space-y-2 text-xs text-text-secondary">
                    <li className="flex items-center gap-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-600" />
                      Database schema: Partitioned by <code className="bg-white border p-0.5 rounded font-bold">tenant_id</code>.
                    </li>
                    <li className="flex items-center gap-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-600" />
                      Cosine calculations: Isolated utilizing Pgvector index scopes.
                    </li>
                    <li className="flex items-center gap-2">
                      <span className="w-1.5 h-1.5 rounded-full bg-emerald-600" />
                      Client scope keys: Isolated to namespace: <code className="bg-white border p-0.5 rounded font-bold">{tenant.slug}</code>.
                    </li>
                  </ul>
                </div>
              </div>
            )}
          </Card>
        </div>

        {/* Right Column: Chibi Guidance & Active Keys (4 cols) */}
        <div className="lg:col-span-4 space-y-6">
          <MascotBubble
            mascot="reading"
            bubbleColor="accent"
            message={
              <div className="space-y-1.5 text-xs text-brand-primary">
                <p className="font-bold font-heading uppercase text-xs">Simulating API Activity</p>
                <p>
                  "We have set up a mock client background worker! It simulates item indexing and user logs to test recommendations logic."
                </p>
              </div>
            }
          />

          <Card title="Active Credentials" padding="sm">
            <div className="space-y-3">
              {tenant.apiKeys.filter(k => k.status === 'ACTIVE').length === 0 ? (
                <p className="text-xs text-text-muted italic text-center py-4">No active API keys found.</p>
              ) : (
                tenant.apiKeys.filter(k => k.status === 'ACTIVE').slice(0, 3).map((key) => (
                  <div 
                    key={key.key}
                    className="p-3 border-2 border-brand-primary rounded bg-bg-base/40 text-left"
                  >
                    <div className="flex justify-between items-center mb-1">
                      <span className="text-xs font-bold text-brand-primary font-heading truncate">{key.name}</span>
                      <span className="text-[8px] bg-emerald-100 text-emerald-800 border border-emerald-600 rounded px-1.5 font-bold uppercase">
                        ACTIVE
                      </span>
                    </div>
                    <code className="text-[10px] text-text-secondary select-all font-mono block truncate">
                      {key.key}
                    </code>
                  </div>
                ))
              )}
              
              <Button 
                variant="outline" 
                size="sm" 
                fullWidth 
                onClick={() => navigate('/dashboard/keys')}
                className="mt-2 text-xs font-heading font-bold"
              >
                Manage Credentials
              </Button>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};
