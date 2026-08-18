import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { store } from '../../mock/store';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { ArrowLeft, Building2, ShieldCheck, Zap, KeyRound } from 'lucide-react';
import mascotReading from '../../assets/Mascot reading book.svg';

const registrationSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  username: z.string().min(3, 'Username must be at least 3 characters'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  tenantName: z.string().min(3, 'Tenant name must be at least 3 characters'),
  tenantSlug: z.string()
    .min(3, 'Tenant slug must be at least 3 characters')
    .max(20, 'Tenant slug cannot exceed 20 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug can only contain lowercase letters, numbers, and hyphens')
});

type RegistrationForm = z.infer<typeof registrationSchema>;

export const TenantRegistration: React.FC = () => {
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors, isSubmitting }, watch, setValue } = useForm<RegistrationForm>({
    resolver: zodResolver(registrationSchema),
    defaultValues: {
      email: '',
      username: '',
      password: '',
      tenantName: '',
      tenantSlug: ''
    }
  });

  const tenantNameValue = watch('tenantName');
  const emailValue = watch('email');

  // Auto-generate slug and username suggestions
  React.useEffect(() => {
    if (tenantNameValue) {
      const suggestedSlug = tenantNameValue
        .toLowerCase()
        .replace(/[^a-z0-9-\s]/g, '')
        .trim()
        .replace(/\s+/g, '-');
      setValue('tenantSlug', suggestedSlug.slice(0, 20));
    }
  }, [tenantNameValue, setValue]);

  React.useEffect(() => {
    if (emailValue && !watch('username')) {
      const suggestedUsername = emailValue.split('@')[0].replace(/[^a-zA-Z0-9_-]/g, '_');
      setValue('username', suggestedUsername);
    }
  }, [emailValue, setValue, watch]);

  const onSubmit = async (data: RegistrationForm) => {
    setErrorMsg(null);
    try {
      const success = await store.signup(
        data.email,
        data.tenantName,
        data.password,
        data.tenantSlug,
        data.username
      );

      if (success) {
        navigate('/dashboard/home');
      } else {
        setErrorMsg('This tenant slug or email is already taken.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Registration failed. This email or organization name may already be in use.');
    }
  };

  return (
    <div className="min-h-screen bg-bg-base flex flex-col justify-between py-12 px-6 md:px-12">
      {/* Top logo header */}
      <div className="max-w-4xl mx-auto w-full flex items-center justify-between mb-8">
        <Link to="/" className="flex items-center gap-2 text-brand-primary">
          <ArrowLeft size={16} />
          <span className="font-heading font-bold text-xs uppercase tracking-wider">Back to landing</span>
        </Link>
        <div className="flex items-center gap-1.5">
          <img src={mascotReading} alt="Susume logo" className="h-7 w-auto" />
          <span className="font-display font-normal text-lg tracking-wider text-brand-primary">SUSUME</span>
        </div>
      </div>

      {/* Main content grid */}
      <div className="max-w-4xl mx-auto w-full grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
        {/* Left Column: Form (7 cols) */}
        <div className="lg:col-span-8 flex flex-col gap-6">
          <Card 
            title="Register Isolated Tenant Workspace" 
            subtitle="Configure workspace credentials and data isolation settings."
          >
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-6 text-left">
              {errorMsg && (
                <div className="p-3 border-2 border-brand-secondary bg-brand-secondary/10 rounded-[4px] text-xs font-bold text-brand-secondary">
                  {errorMsg}
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  label="Admin Email Address"
                  type="email"
                  placeholder="name@company.com"
                  error={errors.email?.message}
                  {...register('email')}
                />
                
                <Input
                  label="Username"
                  placeholder="admin_user"
                  error={errors.username?.message}
                  {...register('username')}
                />
              </div>

              <Input
                label="Password"
                type="password"
                placeholder="••••••••"
                error={errors.password?.message}
                {...register('password')}
              />

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  label="Company / Tenant Name"
                  placeholder="Acme Books"
                  error={errors.tenantName?.message}
                  {...register('tenantName')}
                />

                <Input
                  label="Isolated Slug Identifier"
                  placeholder="acme-books"
                  error={errors.tenantSlug?.message}
                  {...register('tenantSlug')}
                  helperText="Slug used in URL routing"
                />
              </div>

              <div className="border-t-2 border-brand-primary/20 pt-6">
                <Button variant="secondary" size="lg" fullWidth type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Creating Workspace...' : 'Register Workspace'} <ArrowLeft size={16} className="ml-2 rotate-180" />
                </Button>
              </div>
            </form>
          </Card>
        </div>

        {/* Right Column: Information & Slug specs (4 cols) */}
        <div className="lg:col-span-4 flex flex-col gap-6">
          <MascotBubble
            mascot="reading"
            bubbleColor="accent"
            message={
              <div className="space-y-2">
                <p className="font-heading font-bold uppercase text-xs text-brand-primary flex items-center gap-1">
                  <Building2 size={14} /> Slug Isolation Rules
                </p>
                <p className="text-xs">
                  Your tenant slug is unique. It acts as the routing namespace in Spring Boot REST controllers:
                </p>
                <code className="text-[10px] bg-white border border-brand-primary p-1 rounded block truncate">
                  /recommendations?tenant={watch('tenantSlug') || 'acme-books'}
                </code>
              </div>
            }
          />

          <Card title="Workspace Features" padding="md" variant="cream">
            <div className="text-left space-y-3">
              <div className="flex items-center gap-2 text-xs font-bold text-brand-primary font-heading">
                <ShieldCheck size={16} className="text-brand-secondary" />
                Strict Tenant Isolation
              </div>
              <p className="text-[11px] text-text-secondary leading-relaxed font-sans">
                Tenant data, vector embeddings, and API keys are strictly partitioned across PostgreSQL and Redis.
              </p>

              <div className="flex items-center gap-2 text-xs font-bold text-brand-primary font-heading pt-2">
                <Zap size={16} className="text-brand-secondary" />
                ML Candidate Re-Ranking
              </div>
              <p className="text-[11px] text-text-secondary leading-relaxed font-sans">
                Combines 10 heuristic candidate generators in Spring Boot with high performance FastAPI Python rankers.
              </p>

              <div className="flex items-center gap-2 text-xs font-bold text-brand-primary font-heading pt-2">
                <KeyRound size={16} className="text-brand-secondary" />
                API Key Management
              </div>
              <p className="text-[11px] text-text-secondary leading-relaxed font-sans">
                Generate and revoke scoped API credentials for catalog ingestion and real-time recommendation fetching.
              </p>
            </div>
          </Card>
        </div>
      </div>

      {/* Footer */}
      <div className="max-w-4xl mx-auto w-full text-center mt-12 text-[10px] text-text-secondary">
        Susume Enterprise Recommendation Engine • Built for Scalable Personalization
      </div>
    </div>
  );
};
