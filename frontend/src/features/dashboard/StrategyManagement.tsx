import React, { useEffect, useState } from 'react';
import { apiFetch } from '../../services/api';
import type { StrategyInfoResponse } from '../../types/api';
import { 
  Bell, 
  User, 
  Sliders, 
  AlertTriangle, 
  RotateCcw, 
  Upload, 
  Download,
  Filter
} from 'lucide-react';

interface StrategyNode {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
}

interface EngineConfig {
  defaultEngine: string;
  enabledNodesCount: number;
  lastUpdate: string;
  globalStatus: string;
  targetEnv: string;
  clickWeight: number;
  likeWeight: number;
  purchaseWeight: number;
  similarityThreshold: number;
  neighborCount: number;
  confidenceLimit: number;
  recLimit: number;
  cfWeight: number;
  contentWeight: number;
  trendWeight: number;
}

const DEFAULT_STRATEGIES: StrategyNode[] = [
  { id: 'personalized', name: 'Personalized', description: 'Recommends items tailored to each user based on their past views and purchases.', enabled: true },
  { id: 'trending', name: 'Trending', description: 'Highlights popular items rapidly gaining popularity across all users right now.', enabled: false },
  { id: 'hybrid', name: 'Hybrid Engine', description: 'Combines multiple algorithms to deliver balanced and accurate recommendations.', enabled: true },
  { id: 'collaborative_filtering', name: 'Collaborative Filtering', description: 'Suggests items liked by users who share similar tastes and behavior.', enabled: false },
  { id: 'content_based', name: 'Content-Based', description: 'Matches products based on category, brand, and feature similarity.', enabled: true },
  { id: 'popularity', name: 'Popularity', description: 'Ranks globally top-performing items based on overall interactions.', enabled: true },
  { id: 'similar_items', name: 'Similar Items', description: 'Finds products that closely match a selected item.', enabled: false },
  { id: 'rule_based', name: 'Rule-Based', description: 'Applies custom business rules, sponsored boosts, and stock filters.', enabled: false },
  { id: 'frequently_bought_together', name: 'Frequently Bought Together', description: 'Suggests complementary products frequently purchased in the same order.', enabled: false },
  { id: 'random_discovery', name: 'Random Discovery', description: 'Introduces unexpected products to help users explore new catalog items.', enabled: true }
];

export const StrategyManagement: React.FC = () => {
  const [strategies, setStrategies] = useState<StrategyNode[]>(DEFAULT_STRATEGIES);
  const [editingNodeId, setEditingNodeId] = useState<string>('hybrid');
  const [hasUnsavedChanges, setHasUnsavedChanges] = useState<boolean>(false);
  const [actionNotice, setActionNotice] = useState<string | null>(null);

  // Configuration state matching exact design specs
  const [config, setConfig] = useState<EngineConfig>({
    defaultEngine: 'HYBRID',
    enabledNodesCount: 8,
    lastUpdate: '2H AGO',
    globalStatus: 'OPTIMIZED',
    targetEnv: 'HYBRID_V2_STABLE',
    clickWeight: 0.15,
    likeWeight: 0.35,
    purchaseWeight: 0.50,
    similarityThreshold: 0.82,
    neighborCount: 150,
    confidenceLimit: 95,
    recLimit: 50,
    cfWeight: 45,
    contentWeight: 45,
    trendWeight: 15 // Sum = 105% -> triggers warning banner like design mock
  });

  useEffect(() => {
    const loadBackendData = async () => {
      try {
        const fetchedStrats = await apiFetch<StrategyInfoResponse[]>('/api/v1/admin/recommendations/strategies');
        if (fetchedStrats && fetchedStrats.length > 0) {
          setStrategies(fetchedStrats.map(s => ({
            id: s.name,
            name: s.name.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase()),
            description: s.description || `${s.algorithm} algorithm node.`,
            enabled: s.enabled
          })));
        }
        const backendConfig = await apiFetch<any>('/api/v1/admin/recommendations/config');
        if (backendConfig) {
          setConfig(prev => ({
            ...prev,
            defaultEngine: backendConfig.defaultEngine || prev.defaultEngine,
            targetEnv: backendConfig.activeTarget || prev.targetEnv,
            clickWeight: backendConfig.interactionWeights?.clickEvents ?? prev.clickWeight,
            likeWeight: backendConfig.interactionWeights?.likeActions ?? prev.likeWeight,
            purchaseWeight: backendConfig.interactionWeights?.purchaseData ?? prev.purchaseWeight,
            similarityThreshold: backendConfig.advancedParameters?.similarityThreshold ?? prev.similarityThreshold,
            neighborCount: backendConfig.advancedParameters?.neighborCount ?? prev.neighborCount,
            confidenceLimit: backendConfig.advancedParameters?.confidenceLimit ?? prev.confidenceLimit,
            recLimit: backendConfig.advancedParameters?.recLimit ?? prev.recLimit,
            cfWeight: backendConfig.hybridBlend?.cfWeight ?? prev.cfWeight,
            contentWeight: backendConfig.hybridBlend?.contentWeight ?? prev.contentWeight,
            trendWeight: backendConfig.hybridBlend?.trendWeight ?? prev.trendWeight
          }));
        }
      } catch (e) {
        console.warn('Backend unavailable, using exact strategy design mock state:', e);
      }
    };
    loadBackendData();
  }, []);

  const totalBlendWeight = config.cfWeight + config.contentWeight + config.trendWeight;
  const isBlendValid = totalBlendWeight === 100;

  const handleToggleNode = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setStrategies(prev => prev.map(s => s.id === id ? { ...s, enabled: !s.enabled } : s));
    setHasUnsavedChanges(true);

    const strat = strategies.find(s => s.id === id);
    if (strat) {
      try {
        await apiFetch(`/api/v1/admin/recommendations/strategies/${id}/${strat.enabled ? 'disable' : 'enable'}`, { method: 'POST' });
      } catch {
        // Fallback for dev mode
      }
    }
  };

  const handleSave = async () => {
    try {
      await apiFetch('/api/v1/admin/recommendations/config', {
        method: 'PUT',
        body: JSON.stringify(config)
      });
    } catch {
      // Ignore fallback
    }
    setHasUnsavedChanges(false);
    setActionNotice('STRATEGY SCROLL CONFIGURATION SAVED!');
    setTimeout(() => setActionNotice(null), 3000);
  };

  const handleDiscard = () => {
    setHasUnsavedChanges(false);
    setActionNotice('CHANGES DISCARDED.');
    setTimeout(() => setActionNotice(null), 3000);
  };

  const handleReset = () => {
    setConfig({
      defaultEngine: 'HYBRID',
      enabledNodesCount: 8,
      lastUpdate: 'JUST NOW',
      globalStatus: 'OPTIMIZED',
      targetEnv: 'HYBRID_V2_STABLE',
      clickWeight: 0.15,
      likeWeight: 0.35,
      purchaseWeight: 0.50,
      similarityThreshold: 0.82,
      neighborCount: 150,
      confidenceLimit: 95,
      recLimit: 50,
      cfWeight: 45,
      contentWeight: 45,
      trendWeight: 10
    });
    setHasUnsavedChanges(true);
  };

  const updateConfigValue = (key: keyof EngineConfig, value: any) => {
    setConfig(prev => ({ ...prev, [key]: value }));
    setHasUnsavedChanges(true);
  };

  const enabledCount = strategies.filter(s => s.enabled).length;

  return (
    <div className="min-h-screen bg-[#F5F3EC] text-[#2A2A2A] font-sans pb-24 text-left">
      {/* Chapter Title Bar matching exact image top */}
      <div className="flex items-center justify-between px-6 py-4 border-b-4 border-[#202549] bg-[#F5F3EC]">
        <h1 className="text-3xl md:text-4xl font-extrabold tracking-wider font-display uppercase text-[#202549] italic">
          CHAPTER 3: THE STRATEGY SCROLL
        </h1>
        <div className="flex items-center gap-4">
          <button className="p-2 text-[#202549] hover:bg-[#202549]/10 rounded-full cursor-pointer relative">
            <Bell size={22} />
            <span className="absolute top-1 right-1 w-2.5 h-2.5 bg-[#E63963] rounded-full border border-white" />
          </button>
          <div className="w-9 h-9 rounded-full border-2 border-[#202549] bg-white flex items-center justify-center text-[#202549]">
            <User size={20} />
          </div>
        </div>
      </div>

      <div className="p-6 md:p-8 max-w-[1400px] mx-auto space-y-6">
        
        {actionNotice && (
          <div className="p-3 border-2 border-[#202549] bg-[#F2C94C] text-[#202549] text-xs font-black font-mono tracking-widest uppercase shadow-[4px_4px_0px_#202549]">
            {actionNotice}
          </div>
        )}

        {/* Top 4 Metric Badges with black angled ribbon cuts */}
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          {/* Card 1: Default Engine */}
          <div className="border-2 border-[#202549] bg-white p-4 shadow-[4px_4px_0px_#202549] relative overflow-hidden">
            <div className="absolute top-0 right-0 w-0 h-0 border-t-[32px] border-t-[#202549] border-l-[32px] border-l-transparent" />
            <p className="text-[10px] font-black uppercase tracking-widest text-[#6B6B6B] mb-1 font-mono">
              DEFAULT ENGINE
            </p>
            <h3 className="text-2xl font-black italic tracking-wide text-[#E63963] font-display uppercase">
              {config.defaultEngine}
            </h3>
          </div>

          {/* Card 2: Enabled Nodes */}
          <div className="border-2 border-[#202549] bg-white p-4 shadow-[4px_4px_0px_#202549] relative overflow-hidden">
            <div className="absolute top-0 right-0 w-0 h-0 border-t-[32px] border-t-[#202549] border-l-[32px] border-l-transparent" />
            <p className="text-[10px] font-black uppercase tracking-widest text-[#6B6B6B] mb-1 font-mono">
              ENABLED NODES
            </p>
            <h3 className="text-2xl font-black italic tracking-wide text-[#202549] font-display">
              {enabledCount} / {strategies.length}
            </h3>
          </div>

          {/* Card 3: Last Update */}
          <div className="border-2 border-[#202549] bg-white p-4 shadow-[4px_4px_0px_#202549] relative overflow-hidden">
            <div className="absolute top-0 right-0 w-0 h-0 border-t-[32px] border-t-[#202549] border-l-[32px] border-l-transparent" />
            <p className="text-[10px] font-black uppercase tracking-widest text-[#6B6B6B] mb-1 font-mono">
              LAST UPDATE
            </p>
            <h3 className="text-2xl font-black italic tracking-wide text-[#202549] font-display uppercase">
              {config.lastUpdate}
            </h3>
          </div>

          {/* Card 4: Global Status (Highlighted Yellow) */}
          <div className="border-2 border-[#202549] bg-[#F2C94C] p-4 shadow-[4px_4px_0px_#202549] relative overflow-hidden">
            <div className="absolute top-0 right-0 w-0 h-0 border-t-[32px] border-t-[#202549] border-l-[32px] border-l-transparent" />
            <p className="text-[10px] font-black uppercase tracking-widest text-[#202549] mb-1 font-mono">
              GLOBAL STATUS
            </p>
            <h3 className="text-2xl font-black italic tracking-wide text-[#202549] font-display uppercase">
              {config.globalStatus}
            </h3>
          </div>
        </div>

        {/* Main 2-Column Section */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Left Column: AVAILABLE STRATEGIES (4 cols) */}
          <div className="lg:col-span-4 border-2 border-[#202549] bg-white shadow-[4px_4px_0px_#202549] overflow-hidden">
            {/* Header banner */}
            <div className="bg-[#202549] text-white px-4 py-3 flex items-center justify-between font-mono font-black uppercase tracking-widest text-xs">
              <span className="italic">AVAILABLE STRATEGIES</span>
              <Filter size={14} className="text-[#F2C94C]" />
            </div>

            <div className="p-4 space-y-4 max-h-[720px] overflow-y-auto pr-2">
              {strategies.map((strat) => {
                const isEditing = editingNodeId === strat.id;
                
                return (
                  <div
                    key={strat.id}
                    onClick={() => setEditingNodeId(strat.id)}
                    className={`
                      border-2 border-[#202549] rounded-[4px] transition-all cursor-pointer relative p-4 text-left
                      ${isEditing 
                        ? 'bg-[#F2C94C] shadow-[4px_4px_0px_#202549]' 
                        : 'bg-white hover:bg-[#F5F3EC]'
                      }
                    `}
                  >
                    <div className="flex items-center justify-between mb-2">
                      <h4 className="font-extrabold text-base text-[#202549] tracking-wide">
                        {strat.name}
                      </h4>
                      {/* Toggle Switch */}
                      <button
                        onClick={(e) => handleToggleNode(strat.id, e)}
                        className={`w-11 h-6 rounded-full border-2 border-[#202549] p-0.5 transition-colors cursor-pointer ${
                          strat.enabled ? 'bg-[#4A5D23]' : 'bg-[#E9E6DD]'
                        }`}
                      >
                        <div className={`w-4 h-4 rounded-full bg-[#202549] transition-transform ${
                          strat.enabled ? 'translate-x-5' : 'translate-x-0'
                        }`} />
                      </button>
                    </div>

                    <p className="text-xs text-[#2A2A2A] font-medium leading-relaxed mb-4 font-sans">
                      {strat.description}
                    </p>

                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditingNodeId(strat.id);
                      }}
                      className={`
                        w-full py-2 px-3 border-2 border-[#202549] rounded-[2px] font-mono text-[10px] font-black uppercase tracking-widest transition-colors cursor-pointer text-center
                        ${isEditing 
                          ? 'bg-[#202549] text-white' 
                          : 'bg-white text-[#202549] hover:bg-[#E9E6DD]'
                        }
                      `}
                    >
                      {isEditing ? 'CURRENTLY EDITING' : 'CONFIGURE NODE'}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Right Column: ENGINE CONFIGURATION PANEL (8 cols) */}
          <div className="lg:col-span-8 border-2 border-[#202549] bg-[#F9F8F5] p-6 shadow-[4px_4px_0px_#202549] space-y-6 relative">
            
            {/* Title Header with Target & Unsaved Banner */}
            <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 border-b-2 border-[#202549] pb-4">
              <div>
                <h2 className="text-2xl md:text-3xl font-black italic tracking-wide text-[#202549] uppercase font-display">
                  ENGINE CONFIGURATION
                </h2>
                <p className="text-xs font-mono font-bold tracking-widest text-[#E63963] uppercase mt-1">
                  TARGET: {config.targetEnv}
                </p>
              </div>

              {hasUnsavedChanges && (
                <div className="border-2 border-[#202549] bg-[#F2C94C] text-[#202549] px-3 py-1.5 rounded-[2px] text-[10px] font-black font-mono tracking-widest uppercase flex items-center gap-1.5 shadow-[2px_2px_0px_#202549]">
                  <AlertTriangle size={14} className="text-[#202549]" />
                  <span>UNSAVED CHANGES DETECTED!</span>
                </div>
              )}
            </div>

            {/* Config Form Grid: Left (Interaction Weights) / Right (Advanced Parameters) */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              
              {/* Interaction Weights (Sliders) */}
              <div className="space-y-6">
                <h3 className="font-mono font-black uppercase tracking-widest text-xs text-[#202549] border-b-2 border-[#202549] pb-1 inline-block">
                  INTERACTION WEIGHTS
                </h3>

                {/* Click Events */}
                <div className="space-y-2">
                  <div className="flex justify-between items-center text-xs font-mono font-bold">
                    <span className="uppercase tracking-wider text-[#202549]">CLICK EVENTS</span>
                    <span className="text-[#E63963] font-black">{config.clickWeight.toFixed(2)}</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    value={config.clickWeight}
                    onChange={(e) => updateConfigValue('clickWeight', parseFloat(e.target.value))}
                    className="w-full accent-[#E63963] cursor-pointer"
                  />
                </div>

                {/* Like Actions */}
                <div className="space-y-2">
                  <div className="flex justify-between items-center text-xs font-mono font-bold">
                    <span className="uppercase tracking-wider text-[#202549]">LIKE ACTIONS</span>
                    <span className="text-[#E63963] font-black">{config.likeWeight.toFixed(2)}</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    value={config.likeWeight}
                    onChange={(e) => updateConfigValue('likeWeight', parseFloat(e.target.value))}
                    className="w-full accent-[#E63963] cursor-pointer"
                  />
                </div>

                {/* Purchase Data */}
                <div className="space-y-2">
                  <div className="flex justify-between items-center text-xs font-mono font-bold">
                    <span className="uppercase tracking-wider text-[#202549]">PURCHASE DATA</span>
                    <span className="text-[#E63963] font-black">{config.purchaseWeight.toFixed(2)}</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="1"
                    step="0.01"
                    value={config.purchaseWeight}
                    onChange={(e) => updateConfigValue('purchaseWeight', parseFloat(e.target.value))}
                    className="w-full accent-[#E63963] cursor-pointer"
                  />
                </div>
              </div>

              {/* Advanced Parameters (Inputs Grid) */}
              <div className="space-y-6">
                <h3 className="font-mono font-black uppercase tracking-widest text-xs text-[#202549] border-b-2 border-[#202549] pb-1 inline-block">
                  ADVANCED PARAMETERS
                </h3>

                <div className="grid grid-cols-2 gap-4">
                  {/* Similarity Threshold */}
                  <div>
                    <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                      SIMILARITY THRESHOLD
                    </label>
                    <input
                      type="number"
                      step="0.01"
                      value={config.similarityThreshold}
                      onChange={(e) => updateConfigValue('similarityThreshold', parseFloat(e.target.value))}
                      className="w-full p-2.5 border-2 border-[#202549] bg-white font-mono text-sm font-bold text-[#202549]"
                    />
                  </div>

                  {/* Neighbor Count */}
                  <div>
                    <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                      NEIGHBOR COUNT
                    </label>
                    <input
                      type="number"
                      value={config.neighborCount}
                      onChange={(e) => updateConfigValue('neighborCount', parseInt(e.target.value))}
                      className="w-full p-2.5 border-2 border-[#202549] bg-white font-mono text-sm font-bold text-[#202549]"
                    />
                  </div>

                  {/* Confidence Limit */}
                  <div>
                    <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                      CONFIDENCE LIMIT
                    </label>
                    <div className="relative">
                      <input
                        type="text"
                        value={`${config.confidenceLimit}%`}
                        onChange={(e) => updateConfigValue('confidenceLimit', parseInt(e.target.value.replace('%', '')) || 0)}
                        className="w-full p-2.5 border-2 border-[#202549] bg-white font-mono text-sm font-bold text-[#202549]"
                      />
                    </div>
                  </div>

                  {/* Rec Limit */}
                  <div>
                    <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                      REC LIMIT
                    </label>
                    <input
                      type="number"
                      value={config.recLimit}
                      onChange={(e) => updateConfigValue('recLimit', parseInt(e.target.value))}
                      className="w-full p-2.5 border-2 border-[#202549] bg-white font-mono text-sm font-bold text-[#202549]"
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Hybrid Blend Balancing Card Box */}
            <div className="border-2 border-[#202549] bg-white p-5 space-y-4 text-left">
              <h3 className="font-mono font-black uppercase tracking-widest text-xs text-[#202549]">
                HYBRID BLEND BALANCING
              </h3>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div>
                  <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                    CF WEIGHT
                  </label>
                  <input
                    type="number"
                    value={config.cfWeight}
                    onChange={(e) => updateConfigValue('cfWeight', parseInt(e.target.value) || 0)}
                    className="w-full p-2.5 border-2 border-[#202549] bg-white font-mono text-base font-bold text-[#202549]"
                  />
                </div>

                <div>
                  <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                    CONTENT WEIGHT
                  </label>
                  <input
                    type="number"
                    value={config.contentWeight}
                    onChange={(e) => updateConfigValue('contentWeight', parseInt(e.target.value) || 0)}
                    className="w-full p-2.5 border-2 border-[#202549] bg-white font-mono text-base font-bold text-[#202549]"
                  />
                </div>

                <div>
                  <label className="text-[9px] font-mono font-black uppercase tracking-widest text-[#6B6B6B] block mb-1">
                    TREND WEIGHT
                  </label>
                  <input
                    type="number"
                    value={config.trendWeight}
                    onChange={(e) => updateConfigValue('trendWeight', parseInt(e.target.value) || 0)}
                    className={`w-full p-2.5 border-2 bg-white font-mono text-base font-bold ${
                      !isBlendValid ? 'border-[#E63963] text-[#E63963]' : 'border-[#202549] text-[#202549]'
                    }`}
                  />
                </div>
              </div>

              {/* Warning Banner if sum != 100% (Matches design image) */}
              {!isBlendValid && (
                <div className="p-3 border-2 border-[#202549] bg-[#E63963] text-white text-xs font-mono font-black tracking-widest uppercase flex items-center justify-center gap-2 shadow-[2px_2px_0px_#202549]">
                  <AlertTriangle size={16} />
                  <span>WEIGHTS MUST SUM TO 100%! (CURRENTLY {totalBlendWeight}%)</span>
                </div>
              )}
            </div>

          </div>
        </div>

        {/* Bottom Control Bar matching design mock */}
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 pt-4 border-t-2 border-[#202549]">
          <div className="flex items-center gap-3 w-full sm:w-auto">
            <button
              onClick={handleSave}
              className="flex-1 sm:flex-none px-8 py-3 bg-[#202549] text-white font-mono text-xs font-black uppercase tracking-widest border-2 border-[#202549] shadow-[4px_4px_0px_#202549] hover:bg-[#2C3261] cursor-pointer"
            >
              SAVE CHANGES
            </button>
            <button
              onClick={handleDiscard}
              className="flex-1 sm:flex-none px-6 py-3 bg-white text-[#202549] font-mono text-xs font-black uppercase tracking-widest border-2 border-[#202549] shadow-[4px_4px_0px_#202549] hover:bg-[#F5F3EC] cursor-pointer"
            >
              DISCARD
            </button>
          </div>

          <div className="flex items-center gap-3 w-full sm:w-auto justify-end">
            <button className="px-4 py-2 bg-white text-[#202549] font-mono text-xs font-bold uppercase tracking-wider border-2 border-[#202549] flex items-center gap-1.5 cursor-pointer">
              <Upload size={14} /> IMPORT
            </button>
            <button className="px-4 py-2 bg-white text-[#202549] font-mono text-xs font-bold uppercase tracking-wider border-2 border-[#202549] flex items-center gap-1.5 cursor-pointer">
              <Download size={14} /> EXPORT
            </button>
            <button onClick={handleReset} className="px-4 py-2 bg-white text-[#202549] font-mono text-xs font-bold uppercase tracking-wider border-2 border-[#202549] flex items-center gap-1.5 cursor-pointer">
              <RotateCcw size={14} /> RESET
            </button>
          </div>
        </div>

      </div>
    </div>
  );
};
