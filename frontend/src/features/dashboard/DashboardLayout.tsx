import React, { useEffect, useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { store } from '../../mock/store';
import { 
  LayoutDashboard, 
  Key, 
  BookOpen, 
  BarChart3, 
  LogOut, 
  Building,
  Menu,
  X,
  ChevronDown,
  Users,
  Cpu
} from 'lucide-react';
import mascotReading from '../../assets/Mascot reading book.svg';

interface DashboardLayoutProps {
  children: React.ReactNode;
}

/** Skeleton pulse block for loading state */
const SkeletonBlock: React.FC<{ className?: string }> = ({ className = '' }) => (
  <div className={`bg-brand-primary/10 rounded animate-pulse ${className}`} />
);

export const DashboardLayout: React.FC<DashboardLayoutProps> = ({ children }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const [currentUser, setCurrentUser] = useState(store.getCurrentUser());
  const [currentTenant, setCurrentTenant] = useState(store.getCurrentTenant());
  const [tenants, setTenants] = useState(store.getTenants());
  const [showMobileMenu, setShowMobileMenu] = useState(false);
  const [showTenantDropdown, setShowTenantDropdown] = useState(false);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Redirect if not logged in at all
    if (!store.getCurrentUser()) {
      navigate('/login');
      return;
    }

    // Kick off data fetch and track loading
    const loadData = async () => {
      try {
        await store.fetchDashboardData();
      } catch {
        // fetchDashboardData handles errors internally
      } finally {
        setIsLoading(false);
      }
    };

    loadData();

    const unsub = store.subscribe(() => {
      setCurrentUser(store.getCurrentUser());
      setCurrentTenant(store.getCurrentTenant());
      setTenants(store.getTenants());
      setIsLoading(false);
    });

    return unsub;
  }, [navigate]);

  const handleLogout = () => {
    store.logout();
    navigate('/');
  };

  const handleTenantChange = (slug: string) => {
    store.setCurrentTenantSlug(slug);
    setShowTenantDropdown(false);
  };

  const menuItems = [
    { name: 'Overview', path: '/dashboard/home', icon: LayoutDashboard },
    { name: 'Strategy Management', path: '/dashboard/strategy', icon: Cpu },
    { name: 'Team Members', path: '/dashboard/team', icon: Users },
    { name: 'API Key Manager', path: '/dashboard/keys', icon: Key },
    { name: 'API Reference', path: '/dashboard/docs', icon: BookOpen },
    { name: 'Stats & Usage', path: '/dashboard/stats', icon: BarChart3 }
  ];

  // Loading skeleton — renders the sidebar chrome so the screen isn't black
  if (isLoading || !currentUser) {
    return (
      <div className="min-h-screen bg-bg-base flex flex-col md:flex-row">
        {/* Skeleton Sidebar */}
        <aside className="hidden md:flex flex-col w-64 bg-white border-r-2 border-brand-primary min-h-screen">
          <div className="flex items-center gap-2.5 px-6 py-6 border-b-2 border-brand-primary">
            <SkeletonBlock className="w-10 h-10 rounded-[4px]" />
            <SkeletonBlock className="w-24 h-6" />
          </div>
          <div className="px-4 py-5 border-b-2 border-brand-primary">
            <SkeletonBlock className="w-full h-9 rounded-[4px]" />
          </div>
          <nav className="flex-1 px-3 py-6 space-y-2">
            {[...Array(5)].map((_, i) => (
              <SkeletonBlock key={i} className="w-full h-10 rounded-[4px]" />
            ))}
          </nav>
          <div className="p-4 border-t-2 border-brand-primary space-y-3">
            <SkeletonBlock className="w-full h-8 rounded-[4px]" />
            <SkeletonBlock className="w-full h-9 rounded-[4px]" />
          </div>
        </aside>

        {/* Skeleton Main */}
        <main className="flex-1 flex flex-col min-h-screen">
          <header className="hidden md:flex items-center justify-between bg-white px-8 py-5 border-b-2 border-brand-primary">
            <SkeletonBlock className="w-48 h-6" />
            <SkeletonBlock className="w-32 h-6" />
          </header>
          <div className="flex-1 p-6 md:p-8 max-w-7xl w-full mx-auto space-y-6">
            <SkeletonBlock className="w-full h-20 rounded-[8px]" />
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
              {[...Array(4)].map((_, i) => (
                <SkeletonBlock key={i} className="h-28 rounded-[8px]" />
              ))}
            </div>
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
              <SkeletonBlock className="lg:col-span-8 h-80 rounded-[8px]" />
              <SkeletonBlock className="lg:col-span-4 h-80 rounded-[8px]" />
            </div>
          </div>
        </main>
      </div>
    );
  }

  const tenantDisplay = currentTenant ?? { name: '—', slug: '—', id: '' };

  return (
    <div className="min-h-screen bg-bg-base flex flex-col md:flex-row border-brand-primary">
      {/* Mobile Top Bar */}
      <div className="md:hidden flex items-center justify-between bg-white px-6 py-4 border-b-2 border-brand-primary">
        <Link to="/dashboard/home" className="flex items-center gap-2">
          <img src={mascotReading} alt="Susume logo" className="h-8 w-auto" />
          <span className="font-display font-normal text-xl tracking-wider text-brand-primary">SUSUME</span>
        </Link>
        <button 
          onClick={() => setShowMobileMenu(!showMobileMenu)}
          className="p-1.5 border-2 border-brand-primary rounded-[4px] bg-brand-accent hover:bg-brand-accent-hover text-brand-primary cursor-pointer"
        >
          {showMobileMenu ? <X size={20} /> : <Menu size={20} />}
        </button>
      </div>

      {/* Sidebar Navigation */}
      <aside className={`
        fixed inset-0 z-40 md:relative md:translate-x-0 md:flex flex-col
        w-full md:w-64 bg-white border-r-2 border-brand-primary transition-transform duration-200
        ${showMobileMenu ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}
      `}>
        {/* Sidebar Header */}
        <div className="hidden md:flex items-center gap-2.5 px-6 py-6 border-b-2 border-brand-primary">
          <img src={mascotReading} alt="Susume logo" className="h-10 w-auto" />
          <div>
            <h1 className="font-display font-normal text-2xl tracking-widest text-brand-primary leading-none">SUSUME</h1>
            <span className="text-[9px] font-bold tracking-widest text-brand-secondary uppercase">Rec Engine</span>
          </div>
        </div>

        {/* Tenant Selector Dropdown */}
        <div className="px-4 py-5 border-b-2 border-brand-primary bg-bg-base/30 relative">
          <label className="text-[9px] font-bold text-text-secondary uppercase tracking-widest block mb-1">
            Active Tenant Isolated
          </label>
          <button 
            onClick={() => setShowTenantDropdown(!showTenantDropdown)}
            className="w-full flex items-center justify-between bg-white border-2 border-brand-primary rounded-[4px] px-3 py-2 text-sm text-brand-primary font-bold shadow-hard-sm cursor-pointer hover:bg-surface-alt transition-colors"
          >
            <span className="flex items-center gap-2 truncate">
              <Building size={16} className="text-brand-secondary flex-shrink-0" />
              <span className="truncate">{tenantDisplay.name}</span>
            </span>
            <ChevronDown size={16} className={`transition-transform duration-150 ${showTenantDropdown ? 'rotate-180' : ''}`} />
          </button>

          {showTenantDropdown && (
            <div className="absolute left-4 right-4 mt-2 bg-white border-2 border-brand-primary rounded-[4px] shadow-hard z-50">
              <div className="py-1 max-h-48 overflow-y-auto">
                {tenants.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => handleTenantChange(t.slug ?? t.id)}
                    className={`w-full text-left px-4 py-2 text-xs font-bold text-brand-primary hover:bg-brand-accent/20 flex items-center justify-between cursor-pointer ${
                      (t.slug ?? t.id) === (tenantDisplay.slug ?? tenantDisplay.id) ? 'bg-bg-base' : ''
                    }`}
                  >
                    <span>{t.name}</span>
                    <span className="text-[8px] bg-brand-primary text-white rounded px-1.5 py-0.5 ml-2">
                      ADMIN
                    </span>
                  </button>
                ))}
                <div className="border-t-2 border-brand-primary my-1" />
                <button
                  onClick={() => {
                    setShowTenantDropdown(false);
                    setShowMobileMenu(false);
                    navigate('/register');
                  }}
                  className="w-full text-left px-4 py-2 text-xs font-bold text-brand-secondary hover:bg-brand-secondary/15 flex items-center gap-1.5 cursor-pointer"
                >
                  <Building size={12} />
                  <span>+ Register New Tenant</span>
                </button>
              </div>
            </div>
          )}
        </div>

        {/* Navigation Items */}
        <nav className="flex-1 px-3 py-6 space-y-2">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;
            return (
              <Link
                key={item.name}
                to={item.path}
                onClick={() => setShowMobileMenu(false)}
                className={`
                  flex items-center gap-3.5 px-4 py-3 rounded-[4px] text-xs font-heading font-bold uppercase tracking-wider border-2 transition-all select-none
                  ${isActive 
                    ? 'bg-brand-accent text-brand-primary border-brand-primary shadow-hard-sm' 
                    : 'bg-white text-text-secondary border-transparent hover:border-brand-primary hover:text-brand-primary hover:bg-bg-base/30'
                  }
                `}
              >
                <Icon size={18} className={isActive ? 'text-brand-secondary' : 'text-text-secondary'} />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>

        {/* Footer Area with user session & logout */}
        <div className="p-4 border-t-2 border-brand-primary bg-bg-base/20 space-y-3">
          <div className="flex items-center gap-2.5 px-2">
            <div className="w-8 h-8 rounded-full border-2 border-brand-primary bg-brand-accent/30 flex items-center justify-center font-bold text-brand-primary font-heading text-sm">
              {(currentUser.username || currentUser.email).slice(0, 2).toUpperCase()}
            </div>
            <div className="truncate text-left">
              <p className="text-[12px] font-bold text-brand-primary truncate leading-tight">{currentUser.username || currentUser.email.split('@')[0]}</p>
              <p className="text-[10px] text-text-secondary truncate leading-none mt-0.5">{currentUser.email}</p>
            </div>
          </div>
          <button 
            onClick={handleLogout}
            className="w-full flex items-center justify-center gap-2 bg-white border-2 border-brand-primary hover:bg-brand-secondary/10 hover:text-brand-secondary rounded-[4px] px-3 py-2 text-xs font-heading font-bold uppercase tracking-wider text-text-secondary transition-colors cursor-pointer select-none"
          >
            <LogOut size={14} />
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 min-h-screen">
        {/* Top Header Panel */}
        <header className="hidden md:flex items-center justify-between bg-white px-8 py-5 border-b-2 border-brand-primary">
          <div className="flex items-center gap-4 text-left">
            <span className="text-xs font-extrabold uppercase tracking-wider font-heading px-3 py-1.5 border-2 border-brand-primary rounded bg-brand-accent/15 text-brand-primary">
              Multi-Tenant Mode
            </span>
            <span className="text-xs font-semibold text-text-secondary">
              Isolated Workspace / <span className="font-bold text-brand-primary">{tenantDisplay.slug}</span>
            </span>
          </div>
          <div className="flex items-center gap-2 text-xs font-bold text-text-secondary">
            System status: 
            <span className="flex items-center gap-1.5 text-emerald-600 bg-emerald-50 px-2 py-1 border-2 border-emerald-600 rounded">
              <span className="w-2 h-2 rounded-full bg-emerald-600 animate-pulse" />
              ONLINE (ACTIVE)
            </span>
          </div>
        </header>

        {/* Inner Content Grid */}
        <div className="flex-1 p-6 md:p-8 overflow-y-auto max-w-7xl w-full mx-auto">
          {children}
        </div>
      </main>
    </div>
  );
};
