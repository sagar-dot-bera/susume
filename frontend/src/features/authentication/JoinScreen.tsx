import React, { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { store } from '../../mock/store';
import { Input } from '../../components/ui/Input';
import { Button } from '../../components/ui/Button';
import { Card } from '../../components/ui/Card';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { UserCheck, ShieldCheck, CheckCircle2 } from 'lucide-react';
import joinCrewMascot from '../../assets/join_crew_image.svg';

const joinSchema = z.object({
  firstName: z.string().optional(),
  lastName: z.string().optional(),
  username: z.string()
    .min(3, 'Username must be at least 3 characters')
    .regex(/^[a-zA-Z0-9_-]+$/, 'Username can only contain letters, numbers, underscores, and hyphens'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  confirmPassword: z.string().min(6, 'Please confirm your password'),
}).refine((data) => data.password === data.confirmPassword, {
  message: 'Passwords do not match',
  path: ['confirmPassword'],
});

type JoinForm = z.infer<typeof joinSchema>;

export const JoinScreen: React.FC = () => {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();

  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<JoinForm>({
    resolver: zodResolver(joinSchema),
    defaultValues: {
      firstName: '',
      lastName: '',
      username: '',
      password: '',
      confirmPassword: '',
    }
  });

  const onSubmit = async (data: JoinForm) => {
    if (!token) {
      setErrorMsg('Missing invitation token.');
      return;
    }

    setErrorMsg(null);
    try {
      await store.acceptInvitation(token, {
        firstName: data.firstName,
        lastName: data.lastName,
        username: data.username,
        password: data.password,
        confirmPassword: data.confirmPassword,
      });

      setSuccessMsg('Account created successfully! Redirecting to login...');
      setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (err: any) {
      console.error('Accept invitation error:', err);
      setErrorMsg(err?.message || 'Failed to accept invitation. The link may have expired or username is taken.');
    }
  };

  return (
    <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
      <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        {/* Left column: Cartoon graphic and guide (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6 items-center">
          <div className="text-center lg:text-left w-full">
            <span className="text-[10px] font-bold tracking-widest text-brand-secondary uppercase bg-brand-secondary/10 px-2.5 py-1 rounded-[4px] border border-brand-secondary/30 mb-2 inline-block">
              COMPLETE REGISTRATION
            </span>
            <h1 className="font-display font-normal text-4xl text-brand-primary tracking-wide mb-2 uppercase">
              JOIN THE CREW
            </h1>
            <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
              Set up your profile credentials to access your team's isolated workspace.
            </p>
          </div>

          <div className="relative border-2 border-brand-primary rounded-[8px] bg-white p-6 shadow-hard w-64 h-64 flex items-center justify-center">
            <img 
              src={joinCrewMascot} 
              alt="Join Crew Mascot" 
              className="max-h-full max-w-full object-contain"
            />
          </div>

          <MascotBubble
            mascot="signup"
            bubbleColor="accent"
            message={
              <p className="text-xs">
                "Almost done! Pick a unique username and a strong password to finalize your profile."
              </p>
            }
          />
        </div>

        {/* Right column: Join Form Card (7 cols) */}
        <div className="lg:col-span-7">
          <Card 
            title="Create Member Profile" 
            subtitle="Finalize your account details to accept invitation."
            headerAction={<UserCheck className="text-brand-secondary" />}
          >
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 text-left font-sans">
              {errorMsg && (
                <div className="p-3 border-2 border-brand-secondary bg-brand-secondary/10 rounded-[4px] text-xs font-bold text-brand-secondary">
                  {errorMsg}
                </div>
              )}

              {successMsg && (
                <div className="p-3 border-2 border-emerald-600 bg-emerald-50 rounded-[4px] text-xs font-bold text-emerald-700 flex items-center gap-2">
                  <CheckCircle2 size={16} />
                  <span>{successMsg}</span>
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <Input
                  label="First Name (Optional)"
                  placeholder="Ren"
                  error={errors.firstName?.message}
                  {...register('firstName')}
                />

                <Input
                  label="Last Name (Optional)"
                  placeholder="Amamiya"
                  error={errors.lastName?.message}
                  {...register('lastName')}
                />
              </div>

              <Input
                label="Username"
                placeholder="joker_99"
                error={errors.username?.message}
                {...register('username')}
              />

              <Input
                label="Password"
                type="password"
                placeholder="••••••••"
                error={errors.password?.message}
                {...register('password')}
              />

              <Input
                label="Confirm Password"
                type="password"
                placeholder="••••••••"
                error={errors.confirmPassword?.message}
                {...register('confirmPassword')}
              />

              <div className="flex items-start gap-2.5 bg-bg-base p-3 border-2 border-brand-primary rounded-[4px] text-[10px] text-text-secondary leading-relaxed font-sans mt-4">
                <ShieldCheck size={16} className="text-emerald-600 flex-shrink-0 mt-0.5" />
                <span>
                  Your password is securely hashed with BCrypt. You can log in using your email or username once completed.
                </span>
              </div>

              <div className="border-t-2 border-brand-primary/20 pt-4 mt-6">
                <Button 
                  variant="primary" 
                  size="lg" 
                  fullWidth 
                  type="submit"
                  disabled={isSubmitting}
                >
                  {isSubmitting ? 'Creating Profile...' : 'Complete Profile & Join'}
                </Button>
              </div>

              <p className="text-center text-xs font-bold text-text-secondary mt-4 font-sans">
                Already registered?{' '}
                <Link to="/login" className="text-brand-secondary hover:underline">
                  Log In here
                </Link>
              </p>
            </form>
          </Card>
        </div>
      </div>
    </div>
  );
};
