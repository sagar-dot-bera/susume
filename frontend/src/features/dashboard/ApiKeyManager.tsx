import React, { useState, useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { store } from '../../mock/store';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { 
  Plus, 
  Copy, 
  Check, 
  Trash2, 
  ShieldAlert, 
  ShieldCheck
} from 'lucide-react';

const SkeletonBlock: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`bg-brand-primary/10 rounded animate-pulse ${className}`} />
);

const keySchema = z.object({
  name: z.string().min(3, 'Key name must be at least 3 characters').max(30, 'Key name too long')
});

type KeyForm = z.infer<typeof keySchema>;

export const ApiKeyManager: React.FC = () => {
  const [tenant, setTenant] = useState(store.getCurrentTenant());
  const [copiedKey, setCopiedKey] = useState<string | null>(null);
  const [newlyCreatedKey, setNewlyCreatedKey] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors }, reset } = useForm<KeyForm>({
    resolver: zodResolver(keySchema)
  });

  useEffect(() => {
    store.fetchDashboardData();
    return store.subscribe(() => {
      setTenant(store.getCurrentTenant());
    });
  }, []);

  const handleCopy = (keyText: string) => {
    navigator.clipboard.writeText(keyText);
    setCopiedKey(keyText);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const onSubmit = async (data: KeyForm) => {
    const key = await store.generateKey(data.name);
    if (key) {
      setNewlyCreatedKey(key.key);
      reset();
      setTimeout(() => setNewlyCreatedKey(null), 8000);
    }
  };

  const handleRevoke = (keyId: string) => {
    if (confirm('Are you sure you want to revoke this API credential? Client applications using this key will immediately receive 401 Unauthorized responses.')) {
      store.revokeKey(keyId);
    }
  };

  if (!tenant) {
    return (
      <div className="space-y-8 animate-fade-in">
        <SkeletonBlock className="w-full h-20 rounded-[8px]" />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div className="lg:col-span-8 space-y-6">
            <SkeletonBlock className="h-24 rounded-[8px]" />
            <SkeletonBlock className="h-64 rounded-[8px]" />
          </div>
          <SkeletonBlock className="lg:col-span-4 h-64 rounded-[8px]" />
        </div>
      </div>
    );
  }

  const activeKeys = tenant.apiKeys.filter(k => k.status === 'ACTIVE');
  const revokedKeys = tenant.apiKeys.filter(k => k.status === 'REVOKED');

  return (
    <div className="space-y-8 animate-fade-in text-left">
      {/* Title banner */}
      <div className="border-2 border-brand-primary rounded-[8px] p-6 bg-white shadow-hard">
        <h2 className="text-2xl md:text-3xl font-display font-normal text-brand-primary tracking-wide uppercase leading-none">
          API Credentials
        </h2>
        <p className="text-xs text-text-secondary font-medium mt-2 font-sans">
          Authorized SDK tokens allowing external servers to ingest catalogs and fetch recommendations.
        </p>
      </div>

      {/* Main split grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        
        {/* Left: Generate & List Active Keys (8 cols) */}
        <div className="lg:col-span-8 space-y-8">
          
          {/* Newly created key highlight alert */}
          {newlyCreatedKey && (
            <div className="border-2 border-brand-primary rounded-[8px] bg-brand-accent/20 p-5 shadow-hard flex items-start gap-4 animate-fade-in">
              <ShieldCheck className="text-brand-primary flex-shrink-0 mt-1" size={24} />
              <div className="space-y-2 flex-1 min-w-0">
                <h4 className="font-heading font-extrabold uppercase text-xs text-brand-primary">API Key Successfully Generated!</h4>
                <p className="text-xs text-text-secondary font-sans font-medium leading-relaxed">
                  Make sure to copy your raw API key now. For security purposes, this full token will not be displayed again.
                </p>
                <div className="flex items-center gap-2 border-2 border-brand-primary rounded bg-white p-2 text-xs font-mono font-bold w-full select-all">
                  <span className="truncate flex-1">{newlyCreatedKey}</span>
                  <button 
                    onClick={() => handleCopy(newlyCreatedKey)}
                    className="p-1 border-2 border-brand-primary rounded bg-brand-accent hover:bg-brand-accent-hover text-brand-primary cursor-pointer"
                  >
                    {copiedKey === newlyCreatedKey ? <Check size={14} /> : <Copy size={14} />}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* Generate Key Card */}
          <Card 
            title="Create Client Access Credentials" 
            subtitle="Tokens must be scoping-tagged by function."
          >
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
              <div className="flex flex-col sm:flex-row gap-4 items-end">
                <div className="flex-1 w-full">
                  <Input
                    label="Credential Scope Name"
                    placeholder="e.g. Web Store Integration"
                    error={errors.name?.message}
                    {...register('name')}
                  />
                </div>
                <Button variant="secondary" type="submit" className="w-full sm:w-auto h-[46px] whitespace-nowrap">
                  <Plus size={16} /> Generate Key
                </Button>
              </div>
            </form>
          </Card>

          {/* Active Keys List */}
          <Card title="Active Credentials" padding="none">
            {activeKeys.length === 0 ? (
              <div className="py-12 text-center text-xs text-text-secondary font-sans font-semibold">
                No active tokens configured. Generate a credential above to begin querying APIs.
              </div>
            ) : (
              <div className="divide-y-2 divide-brand-primary">
                {activeKeys.map((k) => (
                  <div key={k.id || k.key} className="p-5 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 bg-white first:rounded-t-[6px] last:rounded-b-[6px]">
                    <div className="space-y-1">
                      <h4 className="font-heading font-extrabold text-xs text-brand-primary uppercase tracking-wide">
                        {k.name}
                      </h4>
                      <div className="flex items-center gap-1.5">
                        <code className="text-[10px] bg-bg-base border border-brand-primary px-1.5 py-0.5 rounded text-text-secondary">
                          {k.key.length > 12 ? `${k.key.substring(0, 12)}••••••••` : k.key}
                        </code>
                        <button 
                          onClick={() => handleCopy(k.key)}
                          className="p-1 text-text-secondary hover:text-brand-primary hover:bg-bg-base rounded transition-colors cursor-pointer"
                          title="Copy Key"
                        >
                          {copiedKey === k.key ? <Check size={12} className="text-brand-secondary" /> : <Copy size={12} />}
                        </button>
                      </div>
                      <p className="text-[9px] text-text-muted font-sans font-medium">
                        Created on {new Date(k.createdAt).toLocaleString()}
                      </p>
                    </div>
                    
                    <Button 
                      variant="outline" 
                      size="sm" 
                      onClick={() => handleRevoke(k.id || k.key)}
                      className="border-brand-secondary/30 hover:border-brand-secondary hover:bg-brand-secondary/10 text-brand-secondary"
                    >
                      <Trash2 size={12} className="mr-1.5" /> Revoke Key
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Revoked Keys List */}
          {revokedKeys.length > 0 && (
            <Card title="Revoked Credentials" padding="none" variant="cream">
              <div className="divide-y-2 divide-brand-primary">
                {revokedKeys.map((k) => (
                  <div key={k.id || k.key} className="p-4 flex justify-between items-center bg-bg-base/40 text-left first:rounded-t-[6px] last:rounded-b-[6px]">
                    <div className="space-y-0.5 opacity-60">
                      <h4 className="font-heading font-bold text-xs text-text-secondary uppercase">
                        {k.name}
                      </h4>
                      <code className="text-[10px] font-mono block">
                        {k.key.length > 12 ? `${k.key.substring(0, 12)}••••••••` : k.key}
                      </code>
                    </div>
                    <span className="text-[8px] bg-brand-primary/10 text-text-secondary border border-brand-primary rounded px-2 py-0.5 font-bold uppercase tracking-wider">
                      REVOKED
                    </span>
                  </div>
                ))}
              </div>
            </Card>
          )}
        </div>

        {/* Right: Security & mascot bubble (4 cols) */}
        <div className="lg:col-span-4 space-y-6">
          <MascotBubble
            mascot="warning"
            bubbleColor="accent"
            message={
              <div className="space-y-1.5 text-xs text-brand-primary">
                <p className="font-bold font-heading uppercase text-xs flex items-center gap-1">
                  <ShieldAlert size={14} /> Security Notice
                </p>
                <p>
                  "Never expose write-only API keys in web frontends or client-side bundles. Recommendation queries are safe, but database indexing needs backend protection."
                </p>
              </div>
            }
          />

          <Card title="Query Header Syntax" padding="sm" variant="cream">
            <div className="space-y-3 font-sans text-xs">
              <p className="text-text-secondary font-medium">
                Authorization header must contain your active key. Submissions lacking valid keys reject with a <code className="bg-white px-1 py-0.5 rounded font-bold border border-brand-primary">401</code>.
              </p>
              <div className="border-2 border-brand-primary rounded bg-white p-3 font-mono text-[10px] space-y-1.5">
                <p className="font-bold text-brand-primary border-b border-brand-primary/20 pb-1">HTTP Headers</p>
                <p className="text-brand-secondary">X-API-KEY: <span className="text-text-secondary">sk_live_abc123...</span></p>
                <p className="text-brand-secondary">X-TENANT-SLUG: <span className="text-text-secondary">{tenant.slug}</span></p>
              </div>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};
