import React from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import landingImage from '../../assets/landing_page_image.svg';
import ingestImage from '../../assets/ingest.svg';
import interactImage from '../../assets/interact.svg';
import recommendImage from '../../assets/recommend.svg';
import { ArrowRight, Sparkles, Shield, Zap, Cpu, Layers, Activity } from 'lucide-react';

export const LandingPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-bg-base text-text-primary flex flex-col">
      {/* Editorial Header */}
      <header className="px-6 md:px-12 py-6 border-b-2 border-brand-primary bg-white flex justify-between items-center">
        <div className="flex items-center gap-2">
          <span className="font-display font-normal text-3xl tracking-widest text-brand-primary select-none">SUSUME</span>
          <span className="text-[10px] font-bold text-brand-secondary bg-brand-secondary/10 px-2 py-0.5 border border-brand-secondary rounded-full uppercase tracking-wider hidden sm:inline">
            v1.0 Production
          </span>
        </div>
        <div className="flex items-center gap-4">
          <Link to="/login" className="text-xs font-heading font-bold uppercase tracking-wider text-text-secondary hover:text-brand-primary px-3 py-2 transition-colors">
            Log In
          </Link>
          <Button variant="primary" size="sm" onClick={() => navigate('/register')}>
            Get Started
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
              <span className="text-xs font-bold uppercase tracking-wider font-heading">Two-Stage ML Recommendation Platform</span>
            </div>

            <h1 className="font-display font-normal text-5xl md:text-7xl text-brand-primary tracking-tight leading-none mb-6">
              ADVANCE YOUR <br />
              <span className="text-brand-secondary underline decoration-4 decoration-brand-accent">PERSONALIZATION.</span>
            </h1>

            <p className="font-sans text-base md:text-lg text-text-secondary max-w-xl leading-relaxed mb-8">
              Susume is an enterprise-grade multi-tenant recommendation platform. It combines 10 high-performance Spring Boot candidate generation strategies with Python ML re-ranking for real-time personalization.
            </p>

            <div className="flex flex-col sm:flex-row gap-4">
              <Button variant="secondary" size="lg" onClick={() => navigate('/register')}>
                Create Workspace <ArrowRight size={18} className="ml-2" />
              </Button>
              <Button variant="outline" size="lg" onClick={() => navigate('/login')}>
                Open Console
              </Button>
            </div>

            {/* Architecture Highlights Box */}
            <div className="mt-12 p-4 border-2 border-brand-primary rounded-[8px] bg-bg-base shadow-hard flex items-start gap-4 max-w-lg">
              <div className="p-2 border border-brand-primary bg-brand-accent rounded">
                <Cpu size={24} className="text-brand-primary" />
              </div>
              <div className="text-left">
                <p className="text-xs font-heading font-extrabold uppercase text-brand-primary tracking-wider mb-1">Two-Stage Hybrid Architecture:</p>
                <p className="text-xs font-sans font-medium text-text-secondary">
                  Spring Boot aggregates candidate pools via pgvector and Redis, while Python FastAPI scores candidates using 32 canonical features with real-time fallback.
                </p>
              </div>
            </div>
          </div>

          {/* Right Column: Hero Illustration */}
          <div className="lg:col-span-5 bg-bg-base/30 p-8 flex items-center justify-center relative overflow-hidden">
            <img 
              src={landingImage} 
              alt="Susume Architecture Illustration" 
              className="w-full max-w-md h-auto object-contain border-2 border-brand-primary rounded-[8px] shadow-hard-lg bg-white"
            />
            <div className="absolute top-0 right-0 -mr-16 -mt-16 w-32 h-32 rounded-full border-2 border-dashed border-brand-primary/20 pointer-events-none" />
            <div className="absolute bottom-0 left-0 -ml-16 -mb-16 w-48 h-48 rounded-full border-2 border-dashed border-brand-primary/20 pointer-events-none" />
          </div>
        </section>

        {/* Feature Grid Section */}
        <section className="bg-bg-base px-6 md:px-12 py-16 text-center border-b-2 border-brand-primary">
          <div className="max-w-xl mx-auto mb-16">
            <h2 className="font-display font-normal text-3xl md:text-4xl text-brand-primary tracking-wide mb-4 uppercase">
              Core Engine Architecture
            </h2>
            <p className="text-sm font-sans font-medium text-text-secondary">
              High throughput personalization pipelines built for real-world production scale.
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-8 max-w-6xl mx-auto">
            {/* Ingest Card */}
            <Card className="flex flex-col items-center text-center bg-white" padding="lg">
              <div className="w-full aspect-[4/3] rounded-[4px] border-2 border-brand-primary bg-bg-base p-4 mb-6 flex items-center justify-center">
                <img src={ingestImage} alt="Ingest SVG" className="max-h-full max-w-full object-contain" />
              </div>
              <h3 className="font-heading font-extrabold uppercase text-lg text-brand-primary tracking-wide mb-2">1. Vector Ingestion</h3>
              <p className="text-xs text-text-secondary leading-relaxed font-sans">
                Vectorize item metadata using ONNX Sentence Transformers (`all-MiniLM-L6-v2`) and index embeddings natively in PostgreSQL via `pgvector`.
              </p>
            </Card>

            {/* Interact Card */}
            <Card className="flex flex-col items-center text-center bg-white" padding="lg">
              <div className="w-full aspect-[4/3] rounded-[4px] border-2 border-brand-primary bg-bg-base p-4 mb-6 flex items-center justify-center">
                <img src={interactImage} alt="Interact SVG" className="max-h-full max-w-full object-contain" />
              </div>
              <h3 className="font-heading font-extrabold uppercase text-lg text-brand-primary tracking-wide mb-2">2. Event Streaming</h3>
              <p className="text-xs text-text-secondary leading-relaxed font-sans">
                Ingest views, clicks, likes, and purchases asynchronously via RabbitMQ and Redis to compute real-time user affinity signals.
              </p>
            </Card>

            {/* Recommend Card */}
            <Card className="flex flex-col items-center text-center bg-white" padding="lg">
              <div className="w-full aspect-[4/3] rounded-[4px] border-2 border-brand-primary bg-bg-base p-4 mb-6 flex items-center justify-center">
                <img src={recommendImage} alt="Recommend SVG" className="max-h-full max-w-full object-contain" />
              </div>
              <h3 className="font-heading font-extrabold uppercase text-lg text-brand-primary tracking-wide mb-2">3. ML Re-Ranking</h3>
              <p className="text-xs text-text-secondary leading-relaxed font-sans">
                FastAPI Python microservices score candidate pools using 32 canonical features with automatic Spring strategy fallback under 200ms.
              </p>
            </Card>
          </div>
        </section>

        {/* Benefits Panel Section: Split Columns */}
        <section className="grid grid-cols-1 lg:grid-cols-2 bg-white">
          <div className="p-8 md:p-16 text-left border-b-2 lg:border-b-0 lg:border-r-2 border-brand-primary flex flex-col justify-center">
            <h2 className="font-display font-normal text-3xl md:text-4xl text-brand-primary tracking-wide mb-6 uppercase">
              Enterprise Multi-Tenancy <br />Built For Performance
            </h2>
            <div className="space-y-6">
              <div className="flex gap-4">
                <div className="p-2 border-2 border-brand-primary bg-brand-accent rounded h-10 w-10 flex-shrink-0 flex items-center justify-center">
                  <Shield className="text-brand-primary" size={20} />
                </div>
                <div>
                  <h4 className="text-sm font-bold uppercase text-brand-primary font-heading tracking-wide mb-1">Strict Multi-Tenant Isolation</h4>
                  <p className="text-xs text-text-secondary font-sans leading-relaxed">
                    Database tables, Redis caches, and API keys enforce strict tenant scoping to ensure zero cross-tenant data leakage.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <div className="p-2 border-2 border-brand-primary bg-brand-secondary rounded h-10 w-10 flex-shrink-0 flex items-center justify-center">
                  <Zap className="text-white" size={20} />
                </div>
                <div>
                  <h4 className="text-sm font-bold uppercase text-brand-primary font-heading tracking-wide mb-1">10 Candidate Generators</h4>
                  <p className="text-xs text-text-secondary font-sans leading-relaxed">
                    Collaborative filtering, item similarity, popularity, trending, content-based, and rule-based strategies run in parallel in Spring Boot.
                  </p>
                </div>
              </div>

              <div className="flex gap-4">
                <div className="p-2 border-2 border-brand-primary bg-brand-accent rounded h-10 w-10 flex-shrink-0 flex items-center justify-center">
                  <Activity className="text-brand-primary" size={20} />
                </div>
                <div>
                  <h4 className="text-sm font-bold uppercase text-brand-primary font-heading tracking-wide mb-1">Resilient Fallback SLA</h4>
                  <p className="text-xs text-text-secondary font-sans leading-relaxed">
                    Spring Boot includes circuit-breaking fallbacks so if the ML layer is busy, users still receive high quality heuristic recommendations.
                  </p>
                </div>
              </div>
            </div>
          </div>
          
          <div className="p-8 md:p-16 bg-bg-base/20 flex flex-col justify-center items-center text-center">
            <Card className="w-full max-w-md bg-white text-left" padding="lg">
              <h3 className="font-heading font-extrabold uppercase text-xl text-brand-primary tracking-wide mb-4">Launch Workspace</h3>
              <p className="text-xs text-text-secondary font-sans leading-relaxed mb-6">
                Register an isolated tenant slug, obtain scoped API credentials, and start querying personalized recommendation endpoints.
              </p>
              <div className="space-y-4">
                <Button variant="primary" fullWidth onClick={() => navigate('/register')}>
                  Register Tenant Workspace
                </Button>
                <Button variant="outline" fullWidth onClick={() => navigate('/login')}>
                  Open Admin Console
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
            <span className="text-[10px] uppercase font-bold text-text-muted">Enterprise Multi-Tenant Recommendation Engine</span>
          </div>
          <p className="text-[10px] text-text-muted font-sans font-medium text-center md:text-right">
            © 2026 Susume Inc. All rights reserved. Built for Scalable Personalization.
          </p>
        </div>
      </footer>
    </div>
  );
};
