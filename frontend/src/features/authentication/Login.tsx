import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { store } from '../../mock/store';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { KeyRound, ShieldCheck, Lock, ArrowLeft } from 'lucide-react';

const loginSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  password: z.string().min(1, 'Password is required')
});

type LoginForm = z.infer<typeof loginSchema>;

export const Login: React.FC = () => {
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: 'admin@susume.io',
      password: 'password123'
    }
  });

  const onSubmit = async (data: LoginForm) => {
    setErrorMsg(null);
    try {
      const success = await store.login(data.email, data.password);
      if (success) {
        navigate('/dashboard/home');
      } else {
        setErrorMsg('Invalid email or password.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Invalid email or password.');
    }
  };

  return (
    <div className="min-h-screen bg-bg-base flex flex-col justify-between py-12 px-6">
      {/* Top Header */}
      <div className="max-w-4xl mx-auto w-full flex items-center justify-between mb-4">
        <Link to="/" className="flex items-center gap-2 text-brand-primary">
          <ArrowLeft size={16} />
          <span className="font-heading font-bold text-xs uppercase tracking-wider">Back to landing</span>
        </Link>
        <span className="font-display font-normal text-xl tracking-wider text-brand-primary">SUSUME</span>
      </div>

      <div className="w-full max-w-4xl mx-auto grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        {/* Left Column: Form (7 cols) */}
        <div className="lg:col-span-7">
          <Card 
            title="Administrator Sign In" 
            subtitle="Access your isolated workspace console."
            headerAction={<KeyRound className="text-brand-secondary" />}
          >
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-5 text-left">
              {errorMsg && (
                <div className="p-3 border-2 border-brand-secondary bg-brand-secondary/10 rounded-[4px] text-xs font-bold text-brand-secondary">
                  {errorMsg}
                </div>
              )}

              <Input
                label="Admin Email Address"
                type="email"
                placeholder="name@company.com"
                error={errors.email?.message}
                {...register('email')}
              />

              <Input
                label="Console Password"
                type="password"
                placeholder="••••••••"
                error={errors.password?.message}
                {...register('password')}
              />

              <div className="flex justify-between items-center text-xs font-bold font-sans">
                <label className="flex items-center gap-1.5 cursor-pointer text-text-secondary select-none">
                  <input type="checkbox" className="border-2 border-brand-primary rounded-[2px]" defaultChecked />
                  Remember session
                </label>
                <a href="#" className="text-brand-secondary hover:underline">Forgot password?</a>
              </div>

              <div className="border-t-2 border-brand-primary/20 pt-4 mt-6">
                <Button variant="secondary" size="lg" fullWidth type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Authenticating...' : 'Open Tenant Console'}
                </Button>
              </div>

              <p className="text-center text-xs font-bold text-text-secondary mt-4 font-sans">
                Not registered?{' '}
                <Link to="/register" className="text-brand-secondary hover:underline">
                  Create a new tenant workspace
                </Link>
              </p>
            </form>
          </Card>
        </div>

        {/* Right Column: Clean Enterprise Security Box (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6 text-left">
          <div className="text-left w-full">
            <h1 className="font-display font-normal text-4xl text-brand-primary tracking-wide mb-2 uppercase">
              WELCOME BACK
            </h1>
            <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
              Sign in to manage API keys, inspect recommendation requests, and configure strategy weights.
            </p>
          </div>

          <Card title="Security & Compliance" padding="md" variant="cream">
            <div className="space-y-4 font-sans text-xs">
              <div className="flex items-start gap-3">
                <ShieldCheck size={18} className="text-brand-secondary flex-shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-bold text-brand-primary uppercase">Isolated Workspace Session</h4>
                  <p className="text-[11px] text-text-secondary mt-0.5 leading-relaxed">
                    Authentication issues a JWT bound strictly to your tenant namespace.
                  </p>
                </div>
              </div>

              <div className="flex items-start gap-3 pt-2 border-t border-brand-primary/10">
                <Lock size={18} className="text-brand-primary flex-shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-bold text-brand-primary uppercase">BCrypt Encrypted Auth</h4>
                  <p className="text-[11px] text-text-secondary mt-0.5 leading-relaxed">
                    All administrative passwords are salt-hashed and stored securely in PostgreSQL.
                  </p>
                </div>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Footer */}
      <div className="max-w-4xl mx-auto w-full text-center mt-8 text-[10px] text-text-secondary">
        Susume Enterprise Console • Authenticated Session Protection
      </div>
    </div>
  );
};
