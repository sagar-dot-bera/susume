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
import { ArrowLeft, Building2, Check } from 'lucide-react';
import mascotReading from '../../assets/Mascot reading book.svg';

const registrationSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  username: z.string().min(3, 'Username must be at least 3 characters'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  tenantName: z.string().min(3, 'Tenant name must be at least 3 characters'),
  tenantSlug: z.string()
    .min(3, 'Tenant slug must be at least 3 characters')
    .max(20, 'Tenant slug cannot exceed 20 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug can only contain lowercase letters, numbers, and hyphens'),
  plan: z.enum(['Starter', 'Growth', 'Enterprise']).refine(v => v !== undefined, 'Please select a subscription tier')
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
      tenantSlug: '',
      plan: 'Growth'
    }
  });

  const tenantNameValue = watch('tenantName');
  const emailValue = watch('email');
  const selectedPlan = watch('plan');

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

  const planTiers = [
    {
      id: 'Starter' as const,
      name: 'Starter Plan',
      price: '$49/mo',
      desc: 'Ideal for testing and small catalog sizes up to 1,000 items.',
      features: ['1,000 Catalog Items', '10,000 Monthly Recs', '1 API Key', 'Community Support']
    },
    {
      id: 'Growth' as const,
      name: 'Growth Plan',
      price: '$199/mo',
      desc: 'Perfect for growing ecommerce shops and platforms up to 50k items.',
      features: ['50,000 Catalog Items', '500,000 Monthly Recs', '5 API Keys', 'Sub-150ms Latency SLA', 'Email Support']
    },
    {
      id: 'Enterprise' as const,
      name: 'Enterprise Plan',
      price: '$999/mo',
      desc: 'Custom parameters, maximum performance S3 queues and full isolation.',
      features: ['Unlimited Catalog Items', 'Unlimited Recs', 'Unlimited API Keys', 'Sub-50ms Dedicated Cache', '24/7 SLA Support']
    }
  ];

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
            title="Register New Isolated Tenant" 
            subtitle="Configure workspace settings to isolate recommendation data spaces."
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

              {/* Plans Selector */}
              <div className="space-y-3">
                <label className="text-xs font-bold uppercase tracking-wider text-brand-primary font-heading">
                  Select Subscription Plan Tier
                </label>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  {planTiers.map((p) => {
                    const isSelected = selectedPlan === p.id;
                    return (
                      <div
                        key={p.id}
                        onClick={() => setValue('plan', p.id)}
                        className={`
                          border-2 rounded-[6px] p-4 flex flex-col justify-between cursor-pointer select-none transition-all
                          ${isSelected 
                            ? 'bg-brand-accent/15 border-brand-primary shadow-hard-sm' 
                            : 'bg-white border-brand-primary/30 hover:border-brand-primary hover:bg-bg-base/30'
                          }
                        `}
                      >
                        <div>
                          <div className="flex justify-between items-center mb-1">
                            <span className="font-heading font-extrabold uppercase text-xs text-brand-primary">{p.name}</span>
                            {isSelected && <Check size={14} className="text-brand-secondary stroke-[3px]" />}
                          </div>
                          <span className="font-display font-normal text-lg tracking-wide text-brand-primary">{p.price}</span>
                          <p className="text-[10px] text-text-secondary mt-2 leading-relaxed font-sans">{p.desc}</p>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>

              <div className="border-t-2 border-brand-primary/20 pt-6">
                <Button variant="secondary" size="lg" fullWidth type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Registering Workspace...' : 'Generate Sandbox Workspace'} <ArrowLeft size={16} className="ml-2 rotate-180" />
                </Button>
              </div>
            </form>
          </Card>
        </div>

        {/* Right Column: Chibi explanation & plan summary (4 cols) */}
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

          <Card title="Plan Details" padding="md" variant="cream">
            <div className="text-left space-y-4">
              {planTiers.filter(p => p.id === selectedPlan).map(p => (
                <div key={p.id} className="space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="text-xs font-bold uppercase text-text-secondary">Selected Tier:</span>
                    <span className="text-xs font-bold uppercase bg-brand-primary text-white px-2 py-0.5 rounded">
                      {p.id}
                    </span>
                  </div>
                  <ul className="space-y-1.5">
                    {p.features.map((f, i) => (
                      <li key={i} className="text-xs font-semibold text-brand-primary flex items-center gap-2 font-sans">
                        <span className="w-1.5 h-1.5 rounded-full bg-brand-secondary" />
                        {f}
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          </Card>
        </div>
      </div>

      {/* Tiny Footer */}
      <div className="max-w-4xl mx-auto w-full text-center mt-12 text-[10px] text-text-secondary">
        Having problems registering? Consult our <a href="#" className="underline font-bold text-brand-primary">Support Desk</a>.
      </div>
    </div>
  );
};
