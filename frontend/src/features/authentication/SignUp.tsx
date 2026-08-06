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
import { ShieldCheck, UserPlus } from 'lucide-react';
import mascotSignup from '../../assets/sign_up_chibi_girl.svg';

const signUpSchema = z.object({
  email: z.string().email('Please enter a valid email address'),
  username: z.string().min(3, 'Username must be at least 3 characters'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  tenantName: z.string().min(3, 'Organization Name must be at least 3 characters'),
  tenantSlug: z.string()
    .min(3, 'Slug must be at least 3 characters')
    .regex(/^[a-z0-9-]+$/, 'Slug can only contain lowercase letters, numbers, and dashes'),
  firstName: z.string().optional(),
  lastName: z.string().optional(),
});

type SignUpForm = z.infer<typeof signUpSchema>;

export const SignUp: React.FC = () => {
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors, isSubmitting }, setValue, watch } = useForm<SignUpForm>({
    resolver: zodResolver(signUpSchema),
    defaultValues: {
      email: '',
      username: '',
      password: '',
      tenantName: '',
      tenantSlug: '',
      firstName: '',
      lastName: ''
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

  const onSubmit = async (data: SignUpForm) => {
    setErrorMsg(null);
    try {
      const success = await store.signup(
        data.email,
        data.tenantName,
        data.password,
        data.tenantSlug,
        data.username,
        data.firstName,
        data.lastName
      );

      if (success) {
        navigate('/dashboard/home');
      } else {
        setErrorMsg('This tenant slug or email is already registered.');
      }
    } catch (err: any) {
      setErrorMsg(err.message || 'Registration failed. This email or organization name may already be in use.');
    }
  };

  return (
    <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
      <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        {/* Left column: Cartoon graphic and helpful guide (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6 items-center">
          <div className="text-center lg:text-left w-full">
            <h1 className="font-display font-normal text-4xl text-brand-primary tracking-wide mb-2 uppercase">
              JOIN THE CREW
            </h1>
            <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
              Deploy your vector spaces, build recommendation models, and integrate tenant APIs.
            </p>
          </div>

          <div className="relative border-2 border-brand-primary rounded-[8px] bg-white p-6 shadow-hard w-64 h-64 flex items-center justify-center">
            <img 
              src={mascotSignup} 
              alt="Sign Up Mascot chibi girl" 
              className="max-h-full max-w-full object-contain"
            />
          </div>

          <MascotBubble
            mascot="signup"
            bubbleColor="accent"
            message={
              <p className="text-xs">
                "Welcome! Signing up initializes your isolated tenant space and creates your administrator profile."
              </p>
            }
          />
        </div>

        {/* Right column: SignUp Form Card (7 cols) */}
        <div className="lg:col-span-7">
          <Card 
            title="Create Administrator Account" 
            subtitle="Begin testing recommendation isolation containers."
            headerAction={<UserPlus className="text-brand-secondary" />}
          >
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4 text-left">
              {errorMsg && (
                <div className="p-3 border-2 border-brand-secondary bg-brand-secondary/10 rounded-[4px] text-xs font-bold text-brand-secondary">
                  {errorMsg}
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  label="Admin Email"
                  type="email"
                  placeholder="developer@susume.io"
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

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
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
                label="Console Password"
                type="password"
                placeholder="••••••••"
                error={errors.password?.message}
                {...register('password')}
              />

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                  label="Organization Name"
                  placeholder="Hobby Manga Shop"
                  error={errors.tenantName?.message}
                  {...register('tenantName')}
                />

                <Input
                  label="Tenant Slug Identifier"
                  placeholder="hobby-manga"
                  error={errors.tenantSlug?.message}
                  {...register('tenantSlug')}
                  helperText="Slug used in namespace routing"
                />
              </div>

              <div className="flex items-start gap-2.5 bg-bg-base p-3 border-2 border-brand-primary rounded-[4px] text-[10px] text-text-secondary leading-relaxed font-sans mt-4">
                <ShieldCheck size={16} className="text-emerald-600 flex-shrink-0 mt-0.5" />
                <span>
                  By clicking Register, your isolated workspace starts immediately. You can generate API credentials in your console.
                </span>
              </div>

              <div className="border-t-2 border-brand-primary/20 pt-4 mt-6">
                <Button variant="primary" size="lg" fullWidth type="submit" disabled={isSubmitting}>
                  {isSubmitting ? 'Initializing Workspace...' : 'Create Admin Profile'}
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
