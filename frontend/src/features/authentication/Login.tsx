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
import { KeyRound, HelpCircle } from 'lucide-react';
import mascotLogin from '../../assets/log_in_chibi_girl.svg';

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
    <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
      <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
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

        {/* Right Column: Mascot graphics (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6 items-center">
          <div className="text-center lg:text-left w-full">
            <h1 className="font-display font-normal text-4xl text-brand-primary tracking-wide mb-2 uppercase">
              WELCOME BACK
            </h1>
            <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
              Sign in to manage API keys, inspect recommendation requests, and view statistics.
            </p>
          </div>

          <div className="relative border-2 border-brand-primary rounded-[8px] bg-white p-6 shadow-hard w-64 h-64 flex items-center justify-center">
            <img 
              src={mascotLogin} 
              alt="Log In Mascot chibi girl" 
              className="max-h-full max-w-full object-contain"
            />
          </div>

          <MascotBubble
            mascot="login"
            bubbleColor="accent"
            message={
              <div className="space-y-1.5 text-xs text-brand-primary">
                <p className="font-bold uppercase flex items-center gap-1">
                  <HelpCircle size={14} /> Multi-Tenant Authentication:
                </p>
                <p className="text-[11px] leading-relaxed font-semibold">
                  Signing in automatically loads your tenant context and JWT token scopes!
                </p>
              </div>
            }
          />
        </div>
      </div>
    </div>
  );
};
