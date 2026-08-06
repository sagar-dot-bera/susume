import React from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import landingImage from '../../assets/landing_page_image.svg';
import ingestImage from '../../assets/ingest.svg';
import interactImage from '../../assets/interact.svg';
import recommendImage from '../../assets/recommend.svg';
import chibiDev from '../../assets/dev_mascot_chibi.svg';
import { ArrowRight, Sparkles, Shield, Zap } from 'lucide-react';

export const LandingPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-bg-base text-text-primary flex flex-col">
      {/* Editorial Header */}
      <header className="px-6 md:px-12 py-6 border-b-2 border-brand-primary bg-white flex justify-between items-center">
        <div className="flex items-center gap-2">
          <span className="font-display font-normal text-3xl tracking-widest text-brand-primary select-none">SUSUME</span>
          <span className="text-[10px] font-bold text-brand-secondary bg-brand-secondary/10 px-2 py-0.5 border border-brand-secondary rounded-full uppercase tracking-wider hidden sm:inline">
            v1.0 Mocked
          </span>
        </div>
        <div className="flex items-center gap-4">
          <Link to="/login" className="text-xs font-heading font-bold uppercase tracking-wider text-text-secondary hover:text-brand-primary px-3 py-2 transition-colors">
            Log In
          </Link>
          <Button variant="primary" size="sm" onClick={() => navigate('/signup')}>
            Sign Up
          </Button>
        </div>
      </header>

      {/* Main Body: Editorial Split Layout */}
      <main className="flex-grow flex flex-col">
        {/* Hero Section: 7/5 split */}
        <section className="grid grid-cols-1 lg:grid-cols-12 border-b-2 border-brand-primary bg-white">
          {/* Left Column: Hero Text */}
          <div className="lg:col-span-7 p-8 md:p-16 flex flex-col justify-center text-left border-b-2 lg:border-b-0 lg:border-r-2 border-brand-primary">
            <div className="inline-flex items-center gap-2 bg-brand-accent/30 text-brand-primary px-3.5 py-1.5 border-2 border-brand-primary rounded-[4px] w-fit mb-6 animate-pulse">
              <Sparkles size={16} />
              <span className="text-xs font-bold uppercase tracking-wider font-heading">Semantic Vector Recommendations</span>
            </div>

            <h1 className="font-display font-normal text-5xl md:text-7xl text-brand-primary tracking-tight leading-none mb-6">
              ADVANCE YOUR <br />
              <span className="text-brand-secondary underline decoration-4 decoration-brand-accent">DISCOVERY.</span>
            </h1>

            <p className="font-sans text-base md:text-lg text-text-secondary max-w-xl leading-relaxed mb-8">
              Susume is a multi-tenant recommendation engine that isolates developer sandboxes, ingests catalog metadata, processes real-time clicks, and outputs precise semantically-aligned recommendation lists.
            </p>

            <div className="flex flex-col sm:flex-row gap-4">
              <Button variant="secondary" size="lg" onClick={() => navigate('/signup')}>
                Start Building Now <ArrowRight size={18} className="ml-2" />
              </Button>
              <Button variant="outline" size="lg" onClick={() => navigate('/register')}>
                Register Tenant Slug
              </Button>
            </div>

            {/* Micro-comic layout box */}
            <div className="mt-12 p-4 border-2 border-brand-primary rounded-[8px] bg-bg-base shadow-hard flex items-start gap-4 max-w-lg">
              <img src={chibiDev} alt="Dev chibi" className="w-16 h-16 object-contain flex-shrink-0" />
              <div className="text-left">
                <p className="text-xs font-heading font-extrabold uppercase text-brand-primary tracking-wider mb-1">Onboarding Chibi Says:</p>
                <p className="text-xs font-sans font-medium text-text-secondary">
                  "No credit card required! Run our interactive sandbox, explore mock curl endpoints, generate test keys and see analytics simulate in real-time."
                </p>
              </div>
            </div>
          </div>

          {/* Right Column: Hero Illustration (Spacious) */}
          <div className="lg:col-span-5 bg-bg-base/30 p-8 flex items-center justify-center relative overflow-hidden">
            <img 
              src={landingImage} 
              alt="Manga style landing illustration" 
              className="w-full max-w-md h-auto object-contain border-2 border-brand-primary rounded-[8px] shadow-hard-lg bg-white"
            />
            {/* Visual background decoration */}
            <div className="absolute top-0 right-0 -mr-16 -mt-16 w-32 h-32 rounded-full border-2 border-dashed border-brand-primary/20 pointer-events-none" />
            <div className="absolute bottom-0 left-0 -ml-16 -mb-16 w-48 h-48 rounded-full border-2 border-dashed border-brand-primary/20 pointer-events-none" />
          </div>
        </section>

        {/* Feature Grid Section */}
        <section className="bg-bg-base px-6 md:px-12 py-16 text-center border-b-2 border-brand-primary">
          <div className="max-w-xl mx-auto mb-16">
            <h2 className="font-display font-normal text-3xl md:text-4xl text-brand-primary tracking-wide mb-4">
              THE THREE PILARS OF DEEP RECS
            </h2>
            <p className="text-sm font-sans font-medium text-text-secondary">
              A high performance engine built for modern platforms, isolated by design.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-6xl mx-auto">
            {/* Ingest Card */}
            <Card className="flex flex-col items-center text-center bg-white" padding="lg">
              <div className="w-full aspect-[4/3] rounded-[4px] border-2 border-brand-primary bg-bg-base p-4 mb-6 flex items-center justify-center">
                <img src={ingestImage} alt="Ingest SVG" className="max-h-full max-w-full object-contain" />
              </div>
              <h3 className="font-heading font-extrabold uppercase text-lg text-brand-primary tracking-wide mb-2">1. Ingest Catalog</h3>
              <p className="text-xs text-text-secondary leading-relaxed font-sans">
                Vectorize item metadata using lightweight sentence transformers, representing content features in multi-dimensional space.
              </p>
            </Card>

            {/* Interact Card */}
            <Card className="flex flex-col items-center text-center bg-white" padding="lg">
              <div className="w-full aspect-[4/3] rounded-[4px] border-2 border-brand-primary bg-bg-base p-4 mb-6 flex items-center justify-center">
                <img src={interactImage} alt="Interact SVG" className="max-h-full max-w-full object-contain" />
              </div>
              <h3 className="font-heading font-extrabold uppercase text-lg text-brand-primary tracking-wide mb-2">2. Stream Interactions</h3>
              <p className="text-xs text-text-secondary leading-relaxed font-sans">
                Log clicks, likes, and purchases via lightweight JSON payloads, updating user affinity weight models asynchronously.
              </p>
            </Card>

            {/* Recommend Card */}
            <Card className="flex flex-col items-center text-center bg-white" padding="lg">
              <div className="w-full aspect-[4/3] rounded-[4px] border-2 border-brand-primary bg-bg-base p-4 mb-6 flex items-center justify-center">
                <img src={recommendImage} alt="Recommend SVG" className="max-h-full max-w-full object-contain" />
              </div>
              <h3 className="font-heading font-extrabold uppercase text-lg text-brand-primary tracking-wide mb-2">3. Extract Recs</h3>
              <p className="text-xs text-text-secondary leading-relaxed font-sans">
                Fetch personalized recommendation list candidates in milliseconds. Falling back gracefully to trending metrics when needed.
              </p>
            </Card>
          </div>
        </section>

        {/* Benefits Panel Section: Split Columns */}
        <section className="grid grid-cols-1 lg:grid-cols-2 bg-white">
          <div className="p-8 md:p-16 text-left border-b-2 lg:border-b-0 lg:border-r-2 border-brand-primary flex flex-col justify-center">
            <h2 className="font-display font-normal text-3xl md:text-4xl text-brand-primary tracking-wide mb-6 uppercase">
              Isolated multi-tenancy <br />built for scale
            </h2>
            <div className="space-y-6">
              <div className="flex gap-4">
                <div className="p-2 border-2 border-brand-primary bg-brand-accent rounded h-10 w-10 flex-shrink-0 flex items-center justify-center">
                  <Shield className="text-brand-primary" size={20} />
                </div>
                <div>
                  <h4 className="text-sm font-bold uppercase text-brand-primary font-heading tracking-wide mb-1">Strict Data Partitioning</h4>
                  <p className="text-xs text-text-secondary font-sans leading-relaxed">
                    Tenant databases and embedding spaces are mathematically separate. API keys resolve slugs strictly down to query threads.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <div className="p-2 border-2 border-brand-primary bg-brand-secondary rounded h-10 w-10 flex-shrink-0 flex items-center justify-center">
                  <Zap className="text-white" size={20} />
                </div>
                <div>
                  <h4 className="text-sm font-bold uppercase text-brand-primary font-heading tracking-wide mb-1">Sub-100ms Query Responses</h4>
                  <p className="text-xs text-text-secondary font-sans leading-relaxed">
                    Caching configurations and pre-calculated affinity lists ensure recommendation pipelines deliver quick outputs during traffic spikes.
                  </p>
                </div>
              </div>
            </div>
          </div>
          <div className="p-8 md:p-16 bg-bg-base/20 flex flex-col justify-center items-center text-center">
            <Card className="w-full max-w-md bg-white text-left" padding="lg">
              <h3 className="font-heading font-extrabold uppercase text-xl text-brand-primary tracking-wide mb-4">Try interactive sandbox</h3>
              <p className="text-xs text-text-secondary font-sans leading-relaxed mb-6">
                Create a tenant name, generate API credentials, run mock recommendation queries, and witness dashboard traffic metrics adapt immediately.
              </p>
              <div className="space-y-4">
                <Button variant="primary" fullWidth onClick={() => navigate('/register')}>
                  Register Tenant
                </Button>
                <Button variant="outline" fullWidth onClick={() => navigate('/login')}>
                  Access Dashboard Demo
                </Button>
              </div>
            </Card>
          </div>
        </section>
      </main>

      {/* Editorial Footer */}
      <footer className="bg-brand-primary text-white py-12 px-6 md:px-12 border-t-2 border-brand-primary">
        <div className="max-w-6xl mx-auto flex flex-col md:flex-row justify-between items-center gap-6">
          <div className="text-center md:text-left">
            <span className="font-display font-normal text-2xl tracking-widest block text-brand-accent">SUSUME</span>
            <span className="text-[10px] uppercase font-bold text-text-muted">Creative Recommendations Engine</span>
          </div>
          <p className="text-[10px] text-text-muted font-sans font-medium text-center md:text-right">
            © 2026 Susume Inc. All rights reserved. Created in partnership with Antigravity.
          </p>
        </div>
      </footer>
    </div>
  );
};
