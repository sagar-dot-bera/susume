import React, { useEffect, useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { store } from '../../mock/store';
import type { InvitationValidationResponse } from '../../types/api';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { CheckCircle, XCircle, AlertTriangle, ShieldCheck, Mail, Users } from 'lucide-react';
import acceptInviteMascot from '../../assets/accept_invite_image.svg';

export const InvitationLanding: React.FC = () => {
  const { token } = useParams<{ token: string }>();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [validation, setValidation] = useState<InvitationValidationResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [declined, setDeclined] = useState(false);
  const [declineLoading, setDeclineLoading] = useState(false);

  useEffect(() => {
    if (!token) {
      setErrorMsg('Invalid or missing invitation token.');
      setLoading(false);
      return;
    }

    store.validateInvitation(token)
      .then((res) => {
        setValidation(res);
        setLoading(false);
      })
      .catch((err) => {
        console.error('Validation error:', err);
        setErrorMsg('This invitation link is invalid or has expired.');
        setLoading(false);
      });
  }, [token]);

  const handleDecline = async () => {
    if (!token) return;
    setDeclineLoading(true);
    try {
      await store.declineInvitation(token);
      setDeclined(true);
    } catch (e) {
      setErrorMsg('Failed to decline invitation. It may have already been processed.');
    } finally {
      setDeclineLoading(false);
    }
  };

  const handleAccept = () => {
    if (token) {
      navigate(`/join/${token}`);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
        <div className="border-2 border-brand-primary rounded-[8px] bg-white p-8 shadow-hard text-center max-w-md w-full">
          <div className="animate-spin w-8 h-8 border-4 border-brand-primary border-t-transparent rounded-full mx-auto mb-4" />
          <h2 className="font-display text-xl text-brand-primary uppercase">Validating Invitation...</h2>
          <p className="text-xs text-text-secondary mt-2">Checking invitation credentials with Susume workspace.</p>
        </div>
      </div>
    );
  }

  if (errorMsg || !validation?.valid) {
    return (
      <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
        <div className="w-full max-w-md">
          <Card title="Invitation Invalid" subtitle="Unable to verify invitation token">
            <div className="text-center py-6 space-y-4">
              <div className="w-16 h-16 bg-red-100 border-2 border-brand-secondary rounded-full flex items-center justify-center mx-auto text-brand-secondary">
                <AlertTriangle size={32} />
              </div>
              <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
                {errorMsg || 'This invitation has either expired, been cancelled, or already used.'}
              </p>
              <div className="pt-4 border-t-2 border-brand-primary/20">
                <Link to="/login">
                  <Button variant="primary" fullWidth>
                    Return to Login
                  </Button>
                </Link>
              </div>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  if (declined) {
    return (
      <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
        <div className="w-full max-w-md">
          <Card title="Invitation Declined" subtitle="Decision recorded">
            <div className="text-center py-6 space-y-4">
              <div className="w-16 h-16 bg-gray-100 border-2 border-brand-primary rounded-full flex items-center justify-center mx-auto text-brand-primary">
                <XCircle size={32} />
              </div>
              <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
                You have declined the invitation to join <strong className="text-brand-primary">{validation.tenantName || 'the workspace'}</strong>.
              </p>
              <div className="pt-4 border-t-2 border-brand-primary/20">
                <Link to="/">
                  <Button variant="outline" fullWidth>
                    Back to Home
                  </Button>
                </Link>
              </div>
            </div>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-bg-base flex flex-col justify-center items-center py-12 px-6">
      <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-12 gap-8 items-center">
        {/* Left column: Cartoon graphic and welcome bubble (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6 items-center">
          <div className="text-center lg:text-left w-full">
            <span className="text-[10px] font-bold tracking-widest text-brand-secondary uppercase bg-brand-secondary/10 px-2.5 py-1 rounded-[4px] border border-brand-secondary/30 mb-2 inline-block">
              TEAM INVITATION
            </span>
            <h1 className="font-display font-normal text-4xl text-brand-primary tracking-wide mb-2 uppercase">
              YOU'VE BEEN INVITED!
            </h1>
            <p className="text-xs font-semibold text-text-secondary leading-relaxed font-sans">
              Join your crew on Susume Recommendation Engine.
            </p>
          </div>

          <div className="relative border-2 border-brand-primary rounded-[8px] bg-white p-6 shadow-hard w-64 h-64 flex items-center justify-center">
            <img 
              src={acceptInviteMascot} 
              alt="Accept Invitation Mascot" 
              className="max-h-full max-w-full object-contain"
            />
          </div>

          <MascotBubble
            mascot="happy"
            bubbleColor="accent"
            message={
              <p className="text-xs">
                "An invitation grants you direct access to join an isolated workspace with custom team permissions!"
              </p>
            }
          />
        </div>

        {/* Right column: Invitation Card (7 cols) */}
        <div className="lg:col-span-7">
          <Card 
            title={`Join ${validation.tenantName || 'Organization'}`}
            subtitle="Review invitation details below before proceeding."
            headerAction={<Users className="text-brand-secondary" />}
          >
            <div className="space-y-6 text-left">
              <div className="bg-bg-base p-4 border-2 border-brand-primary rounded-[6px] space-y-3 font-sans">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-text-secondary flex items-center gap-1.5">
                    <Mail size={14} className="text-brand-primary" /> Invited Email:
                  </span>
                  <span className="text-xs font-bold text-brand-primary bg-white px-2 py-0.5 border border-brand-primary/30 rounded-[4px]">
                    {validation.email}
                  </span>
                </div>

                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-text-secondary flex items-center gap-1.5">
                    <ShieldCheck size={14} className="text-brand-primary" /> Assigned Role:
                  </span>
                  <span className="text-xs font-bold uppercase text-brand-secondary bg-brand-secondary/10 px-2 py-0.5 border border-brand-secondary/30 rounded-[4px]">
                    {validation.role || 'MEMBER'}
                  </span>
                </div>

                {validation.tenantSlug && (
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-text-secondary">Tenant Slug:</span>
                    <span className="text-xs font-mono text-brand-primary">{validation.tenantSlug}</span>
                  </div>
                )}
              </div>

              <div className="flex items-start gap-2.5 bg-amber-50 p-3 border-2 border-brand-accent rounded-[4px] text-[11px] text-brand-primary leading-relaxed font-sans">
                <CheckCircle size={16} className="text-amber-600 flex-shrink-0 mt-0.5" />
                <span>
                  Accepting this invite lets you set up your username and password to start collaborating immediately.
                </span>
              </div>

              <div className="border-t-2 border-brand-primary/20 pt-4 mt-6 flex flex-col sm:flex-row gap-4">
                <Button variant="primary" size="lg" fullWidth onClick={handleAccept}>
                  Accept & Join Crew
                </Button>
                <Button 
                  variant="outline" 
                  size="lg" 
                  fullWidth 
                  onClick={handleDecline}
                  disabled={declineLoading}
                >
                  {declineLoading ? 'Declining...' : 'Decline'}
                </Button>
              </div>

              <p className="text-center text-xs font-bold text-text-secondary mt-4 font-sans">
                Need to log into another account?{' '}
                <Link to="/login" className="text-brand-secondary hover:underline">
                  Log In here
                </Link>
              </p>
            </div>
          </Card>
        </div>
      </div>
    </div>
  );
};
