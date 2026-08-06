import React, { useState, useEffect } from 'react';
import { store } from '../../mock/store';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { RefreshCw } from 'lucide-react';

const SkeletonBlock: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`bg-brand-primary/10 rounded animate-pulse ${className}`} />
);

export const StatsUsage: React.FC = () => {
  const [tenant, setTenant] = useState(store.getCurrentTenant());
  const [stats, setStats] = useState(store.getStats());
  const [isSimulating, setIsSimulating] = useState(false);

  useEffect(() => {
    store.fetchDashboardData();
    return store.subscribe(() => {
      setTenant(store.getCurrentTenant());
      setStats(store.getStats());
    });
  }, []);

  const handleRefresh = async () => {
    setIsSimulating(true);
    try {
      await store.fetchDashboardData();
    } catch (e) {
      console.error(e);
    } finally {
      setIsSimulating(false);
    }
  };

  if (!tenant) {
    return (
      <div className="space-y-8 animate-fade-in">
        <SkeletonBlock className="w-full h-20 rounded-[8px]" />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          <div className="lg:col-span-8 space-y-6">
            <SkeletonBlock className="h-80 rounded-[8px]" />
            <SkeletonBlock className="h-64 rounded-[8px]" />
          </div>
          <div className="lg:col-span-4 space-y-4">
            <SkeletonBlock className="h-40 rounded-[8px]" />
            <SkeletonBlock className="h-48 rounded-[8px]" />
          </div>
        </div>
      </div>
    );
  }

  // Transform hitsOverTime map into array safely
  const rawHits = stats.hitsOverTime || {};
  const hitsList = Array.isArray(rawHits) 
    ? rawHits 
    : Object.entries(rawHits).map(([date, hits]) => ({ date, hits: Number(hits) }));

  const defaultHits = [
    { date: '00:00', hits: 25 },
    { date: '04:00', hits: 40 },
    { date: '08:00', hits: 85 },
    { date: '12:00', hits: 130 },
    { date: '16:00', hits: 95 },
    { date: '20:00', hits: 110 },
    { date: '23:59', hits: 60 }
  ];

  const chartData = hitsList.length > 0 ? hitsList : defaultHits;

  // Mock Top Items
  const topItems = [
    { rank: '1', id: 'manga-905', name: 'Jujutsu Kaisen, Vol. 21', tags: ['action', 'shonen'], queries: 1420, rate: '92%' },
    { rank: '2', id: 'manga-102', name: 'Chainsaw Man, Vol. 1', tags: ['dark fantasy'], queries: 1042, rate: '86%' },
    { rank: '3', id: 'manga-502', name: 'Spy x Family, Vol. 9', tags: ['comedy', 'action'], queries: 820, rate: '74%' },
    { rank: '4', id: 'book-801', name: 'Clean Code: Handbook', tags: ['programming', 'tech'], queries: 641, rate: '68%' },
    { rank: '5', id: 'prod-101', name: 'Retro Mechanical Keyboard', tags: ['hardware', 'gadgets'], queries: 320, rate: '52%' }
  ];

  const viewCount = Number(stats.typeBreakdown?.VIEW ?? 210);
  const clickCount = Number(stats.typeBreakdown?.CLICK ?? 120);
  const likeCount = Number(stats.typeBreakdown?.LIKE ?? 62);
  const purchaseCount = Number(stats.typeBreakdown?.PURCHASE ?? 22);

  const totalInteractions = (viewCount + clickCount + likeCount + purchaseCount) || 1;

  // Colors for interaction categories
  const interactionTypes = [
    { type: 'VIEW', count: viewCount, percentage: Math.round((viewCount / totalInteractions) * 100), color: 'bg-brand-primary' },
    { type: 'CLICK', count: clickCount, percentage: Math.round((clickCount / totalInteractions) * 100), color: 'bg-brand-accent' },
    { type: 'LIKE', count: likeCount, percentage: Math.round((likeCount / totalInteractions) * 100), color: 'bg-brand-secondary' },
    { type: 'PURCHASE', count: purchaseCount, percentage: Math.round((purchaseCount / totalInteractions) * 100), color: 'bg-emerald-600' }
  ];

  return (
    <div className="space-y-8 animate-fade-in text-left">
      {/* Title Header */}
      <div className="border-2 border-brand-primary rounded-[8px] p-6 bg-white shadow-hard flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h2 className="text-2xl md:text-3xl font-display font-normal text-brand-primary tracking-wide uppercase leading-none">
            Usage Analytics
          </h2>
          <p className="text-xs text-text-secondary font-medium mt-2 font-sans">
            Metrics analyzing user event counts, vector calculations latency, and top items.
          </p>
        </div>
        <Button 
          variant="outline" 
          size="sm" 
          onClick={handleRefresh}
          className="flex items-center gap-2"
        >
          <RefreshCw size={14} className={isSimulating ? 'animate-spin' : ''} /> 
          <span>Refresh Data</span>
        </Button>
      </div>

      {/* Grid: 8/4 split */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        
        {/* Left Column: Charts and tables (8 cols) */}
        <div className="lg:col-span-8 space-y-8">
          
          {/* Recommendation Queries Graph Card */}
          <Card 
            title="Recommendation Queries Over Time" 
            subtitle="Hourly traffic density of vector matching queries."
          >
            <div className="h-64 flex items-end justify-between gap-2.5 pt-8 border-b-2 border-brand-primary pb-2.5 bg-white relative">
              
              {/* Background guidelines */}
              <div className="absolute top-1/4 left-0 right-0 border-t border-brand-primary/10 w-full" />
              <div className="absolute top-2/4 left-0 right-0 border-t border-brand-primary/10 w-full" />
              <div className="absolute top-3/4 left-0 right-0 border-t border-brand-primary/10 w-full" />

              {chartData.map((pt, i) => {
                const heights = ['h-[25%]', 'h-[38%]', 'h-[62%]', 'h-[50%]', 'h-[75%]', 'h-[85%]', 'h-[95%]'];
                const hClass = heights[i % heights.length];
                
                return (
                  <div key={i} className="flex-1 flex flex-col items-center gap-2 group z-10">
                    <div className="text-[10px] font-bold text-text-secondary opacity-0 group-hover:opacity-100 transition-opacity bg-brand-primary text-white px-1.5 py-0.5 rounded shadow-hard-sm absolute -translate-y-8">
                      {pt.hits} queries
                    </div>
                    {/* The bar graphic */}
                    <div 
                      className={`w-full max-w-[48px] ${hClass} bg-brand-accent border-2 border-brand-primary rounded-t-[4px] shadow-hard-sm transition-all duration-300 group-hover:bg-brand-secondary`}
                    />
                    <span className="text-[10px] font-bold text-text-secondary uppercase tracking-wider font-heading mt-1 truncate max-w-full">
                      {pt.date}
                    </span>
                  </div>
                );
              })}
            </div>
            
            <div className="flex justify-between items-center text-xs text-text-secondary font-medium font-sans mt-3">
              <span>Traffic Scope: Live Session</span>
              <span className="flex items-center gap-1.5 font-bold text-brand-primary uppercase">
                <span className="w-2.5 h-2.5 rounded-full bg-brand-accent border border-brand-primary" /> Max peak: 130 queries/hr
              </span>
            </div>
          </Card>

          {/* Top Recommendations Table */}
          <Card title="Top Performing Recommendations" subtitle="Items receiving maximum vector match scores and user interaction.">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b-2 border-brand-primary font-heading font-extrabold uppercase tracking-wider text-text-secondary">
                    <th className="py-3 px-2 text-center w-12">Rank</th>
                    <th className="py-3 px-4">Item Name</th>
                    <th className="py-3 px-4">Tag Categories</th>
                    <th className="py-3 px-4 text-right">Matching Queries</th>
                    <th className="py-3 px-4 text-right">Hit Rate</th>
                  </tr>
                </thead>
                <tbody className="divide-y-2 divide-brand-primary/10">
                  {topItems.map((item) => (
                    <tr key={item.rank} className="hover:bg-bg-base/30 transition-colors font-sans">
                      <td className="py-4 px-2 text-center">
                        <span className="inline-flex items-center justify-center font-heading font-bold bg-brand-accent text-brand-primary border border-brand-primary rounded w-6 h-6 shadow-hard-sm">
                          {item.rank}
                        </span>
                      </td>
                      <td className="py-4 px-4 font-bold text-brand-primary">{item.name}</td>
                      <td className="py-4 px-4">
                        <div className="flex gap-1.5 flex-wrap">
                          {item.tags.map((t, idx) => (
                            <span key={idx} className="bg-white border border-brand-primary text-[9px] font-bold uppercase tracking-wider rounded px-1.5 py-0.5">
                              {t}
                            </span>
                          ))}
                        </div>
                      </td>
                      <td className="py-4 px-4 text-right font-semibold text-text-primary">{item.queries.toLocaleString()}</td>
                      <td className="py-4 px-4 text-right font-extrabold text-brand-secondary">{item.rate}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        </div>

        {/* Right Column: Interaction breakdown & mascots (4 cols) */}
        <div className="lg:col-span-4 space-y-6">
          
          {/* Mascot bubble */}
          <MascotBubble
            mascot="reading"
            bubbleColor="accent"
            message={
              <div className="space-y-1.5 text-xs text-brand-primary">
                <p className="font-bold font-heading uppercase text-xs">A/B Testing Insights</p>
                <p>
                  "We have verified that semantic filtering increases click rates by 22% compared to standard keyword matches."
                </p>
              </div>
            }
          />

          {/* Event Distribution card */}
          <Card title="Interactions Breakdown" padding="md">
            <div className="space-y-4">
              <div className="h-6 w-full flex border-2 border-brand-primary rounded bg-bg-base overflow-hidden">
                {interactionTypes.map((t, i) => (
                  <div 
                    key={i} 
                    className={`${t.color} h-full border-r border-brand-primary last:border-r-0`} 
                    style={{ width: `${Math.max(t.percentage, 5)}%` }}
                    title={`${t.type}: ${t.count}`}
                  />
                ))}
              </div>

              {/* Legend checklist */}
              <div className="space-y-3 pt-2">
                {interactionTypes.map((t, i) => (
                  <div key={i} className="flex justify-between items-center text-xs">
                    <div className="flex items-center gap-2">
                      <span className={`w-3 h-3 rounded-full border border-brand-primary ${t.color}`} />
                      <span className="font-heading font-extrabold uppercase text-text-secondary tracking-wide">{t.type}</span>
                    </div>
                    <div className="font-bold text-brand-primary">
                      {t.count.toLocaleString()} <span className="text-[10px] text-text-muted font-normal font-sans">({t.percentage}%)</span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </Card>

          {/* Latency SLAs */}
          <Card title="Latency Breakdown" padding="sm" variant="cream">
            <div className="space-y-3 font-sans text-xs">
              <div className="flex justify-between items-center border-b border-brand-primary/10 pb-2">
                <span className="font-semibold text-text-secondary">Embedding Creation:</span>
                <span className="font-mono font-bold text-brand-primary">~65ms</span>
              </div>
              <div className="flex justify-between items-center border-b border-brand-primary/10 pb-2">
                <span className="font-semibold text-text-secondary">Vector Matching:</span>
                <span className="font-mono font-bold text-brand-primary">~32ms</span>
              </div>
              <div className="flex justify-between items-center pb-1">
                <span className="font-semibold text-text-secondary">Cache Lookups:</span>
                <span className="font-mono font-bold text-emerald-600 font-bold">~2.4ms</span>
              </div>
            </div>
          </Card>
        </div>

      </div>
    </div>
  );
};
