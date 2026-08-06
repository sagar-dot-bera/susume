import React, { useState, useEffect } from 'react';
import { store } from '../../mock/store';
import type { InvitationResponse } from '../../types/api';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Input } from '../../components/ui/Input';
import { MascotBubble } from '../../components/ui/MascotBubble';
import { Users, UserPlus, Mail, Shield, RefreshCw, Trash2, Clock, CheckCircle, Copy, AlertCircle } from 'lucide-react';

export const TeamMembers: React.FC = () => {
  const [invitations, setInvitations] = useState<InvitationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState('');
  const [inviteRole, setInviteRole] = useState<'MEMBER' | 'ADMIN'>('MEMBER');
  const [modalLoading, setModalLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [createdInviteUrl, setCreatedInviteUrl] = useState<string | null>(null);
  const [copiedToken, setCopiedToken] = useState(false);

  const currentTenant = store.getCurrentTenant();
  const currentUser = store.getCurrentUser();

  const loadData = async () => {
    setLoading(true);
    try {
      const res = await store.fetchInvitations();
      setInvitations(res);
    } catch (e) {
      console.error('Error fetching invitations:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
    // Only sync invitation state from the store's cached data on notify
    // Do NOT call loadData() inside subscribe — it triggers re-notifications
    const unsubscribe = store.subscribe(() => {
      const fresh = store.getCurrentTenant();
      // no-op sync; invitations are reloaded explicitly on user actions
      void fresh;
    });
    return unsubscribe;
  }, []);

  const handleSendInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail) return;

    setModalLoading(true);
    setErrorMsg(null);
    setSuccessMsg(null);
    setCreatedInviteUrl(null);

    try {
      const created = await store.createInvitation(inviteEmail, inviteRole);
      setSuccessMsg(`Invitation sent successfully to ${inviteEmail}!`);
      
      // Build sample shareable link if token/id is returned
      if (created?.id) {
        const sampleUrl = `${window.location.origin}/#/invitation/${created.id}`;
        setCreatedInviteUrl(sampleUrl);
      }

      setInviteEmail('');
      setInviteRole('MEMBER');
      await loadData();
    } catch (err: any) {
      console.error('Failed to invite member:', err);
      setErrorMsg(err?.message || 'Failed to send invitation. Check if email is valid or already invited.');
    } finally {
      setModalLoading(false);
    }
  };

  const handleResend = async (id: string) => {
    try {
      await store.resendInvitation(id);
      setSuccessMsg('Invitation token reset and resent.');
      await loadData();
    } catch (e) {
      setErrorMsg('Failed to resend invitation.');
    }
  };

  const handleCancel = async (id: string) => {
    if (!window.confirm('Are you sure you want to cancel this pending invitation?')) return;
    try {
      await store.cancelInvitation(id);
      setSuccessMsg('Invitation cancelled successfully.');
      await loadData();
    } catch (e) {
      setErrorMsg('Failed to cancel invitation.');
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedToken(true);
    setTimeout(() => setCopiedToken(false), 2000);
  };

  const pendingInvitations = invitations.filter((inv) => inv.status === 'PENDING');
  const acceptedInvitations = invitations.filter((inv) => inv.status === 'ACCEPTED');

  return (
    <div className="space-y-8 font-sans">
      {/* Top Header & Action */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 border-b-2 border-brand-primary/20 pb-6">
        <div>
          <span className="text-[10px] font-bold tracking-widest text-brand-secondary uppercase bg-brand-secondary/10 px-2.5 py-1 rounded-[4px] border border-brand-secondary/30 mb-2 inline-block">
            WORKSPACE MANAGEMENT
          </span>
          <h1 className="font-display text-3xl font-normal text-brand-primary tracking-wide uppercase">
            CREW MEMBERS & INVITATIONS
          </h1>
          <p className="text-xs text-text-secondary mt-1 font-semibold">
            Manage teammate access to {currentTenant?.name || 'your workspace'}, invite new members, and review pending tokens.
          </p>
        </div>

        <Button 
          variant="primary" 
          size="md" 
          onClick={() => {
            setIsModalOpen(true);
            setErrorMsg(null);
            setSuccessMsg(null);
            setCreatedInviteUrl(null);
          }}
          className="flex items-center gap-2 self-start md:self-auto"
        >
          <UserPlus size={16} />
          <span>Invite New Member</span>
        </Button>
      </div>

      {/* Success / Error Banners */}
      {successMsg && (
        <div className="p-3 border-2 border-emerald-600 bg-emerald-50 rounded-[4px] text-xs font-bold text-emerald-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <CheckCircle size={16} className="text-emerald-600" />
            <span>{successMsg}</span>
          </div>
          <button onClick={() => setSuccessMsg(null)} className="text-xs text-emerald-700 underline">Dismiss</button>
        </div>
      )}

      {errorMsg && (
        <div className="p-3 border-2 border-brand-secondary bg-brand-secondary/10 rounded-[4px] text-xs font-bold text-brand-secondary flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle size={16} />
            <span>{errorMsg}</span>
          </div>
          <button onClick={() => setErrorMsg(null)} className="text-xs text-brand-secondary underline">Dismiss</button>
        </div>
      )}

      {/* Main Grid: Active Members & Pending Invites */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Active Members Card (7 cols) */}
        <div className="lg:col-span-7 space-y-4">
          <Card
            title="Active Crew Members"
            subtitle="Current authenticated users with access to this tenant container."
            headerAction={<Users size={20} className="text-brand-primary" />}
          >
            <div className="space-y-3 pt-2">
              {/* Primary Admin / Current User */}
              <div className="flex items-center justify-between p-3.5 border-2 border-brand-primary bg-white rounded-[6px] shadow-sm">
                <div className="flex items-center gap-3">
                  <div className="w-9 h-9 border-2 border-brand-primary bg-brand-primary text-white font-display text-sm font-bold flex items-center justify-center rounded-[4px]">
                    {(currentUser?.username || currentUser?.email || 'AD').slice(0, 2).toUpperCase()}
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-bold text-brand-primary">{currentUser?.username || currentUser?.email?.split('@')[0]}</span>
                      <span className="text-[10px] font-bold text-amber-700 bg-amber-100 border border-amber-300 px-1.5 py-0.5 rounded-[3px]">
                        YOU
                      </span>
                    </div>
                    <span className="text-[11px] text-text-secondary font-semibold block">{currentUser?.email || currentTenant?.contactEmail}</span>
                  </div>
                </div>
                <span className="text-[10px] font-bold tracking-wider uppercase text-brand-secondary bg-brand-secondary/10 border border-brand-secondary/30 px-2 py-1 rounded-[4px]">
                  ADMIN
                </span>
              </div>

              {/* Accepted Members */}
              {acceptedInvitations.map((member) => (
                <div key={member.id} className="flex items-center justify-between p-3.5 border-2 border-brand-primary/40 bg-white rounded-[6px]">
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 border-2 border-brand-primary bg-bg-base text-brand-primary font-display text-sm font-bold flex items-center justify-center rounded-[4px]">
                      {member.email.slice(0, 2).toUpperCase()}
                    </div>
                    <div>
                      <span className="text-xs font-bold text-brand-primary block">{member.email}</span>
                      <span className="text-[11px] text-text-secondary font-semibold">
                        Joined {new Date(member.acceptedAt || member.createdAt).toLocaleDateString()}
                      </span>
                    </div>
                  </div>
                  <span className="text-[10px] font-bold tracking-wider uppercase text-brand-primary bg-bg-base border border-brand-primary/30 px-2 py-1 rounded-[4px]">
                    {member.role || 'MEMBER'}
                  </span>
                </div>
              ))}

              {acceptedInvitations.length === 0 && (
                <p className="text-xs text-text-secondary italic text-center py-4">
                  No additional crew members accepted invites yet.
                </p>
              )}
            </div>
          </Card>
        </div>

        {/* Mascot / Quick Tips (5 cols) */}
        <div className="lg:col-span-5 flex flex-col gap-6">
          <MascotBubble
            mascot="dev"
            bubbleColor="accent"
            message={
              <div className="space-y-1.5">
                <p className="text-xs font-bold text-brand-primary">Team Role Isolation</p>
                <p className="text-[11px] leading-relaxed text-text-secondary">
                  ADMIN members can issue or regenerate primary API keys, while MEMBER accounts can view stats and manage vectors.
                </p>
              </div>
            }
          />

          <div className="border-2 border-brand-primary rounded-[8px] bg-white p-5 shadow-hard space-y-3">
            <h3 className="font-display text-sm text-brand-primary uppercase flex items-center gap-2">
              <Shield size={16} className="text-brand-secondary" /> Access Governance
            </h3>
            <p className="text-xs text-text-secondary leading-relaxed font-semibold">
              Invitations expire automatically after 7 days. You can cancel pending invitations at any time to invalidate unaccepted tokens.
            </p>
          </div>
        </div>
      </div>

      {/* Pending Invitations Section */}
      <div className="space-y-4 pt-4">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-xl text-brand-primary uppercase flex items-center gap-2">
            <Clock size={20} className="text-brand-secondary" /> Pending Invitations ({pendingInvitations.length})
          </h2>
          <Button variant="outline" size="sm" onClick={loadData} className="flex items-center gap-1.5">
            <RefreshCw size={12} className={loading ? 'animate-spin' : ''} />
            <span>Refresh</span>
          </Button>
        </div>

        <Card title="" subtitle="">
          {loading ? (
            <div className="py-8 text-center text-xs font-semibold text-text-secondary">
              <div className="animate-spin w-6 h-6 border-2 border-brand-primary border-t-transparent rounded-full mx-auto mb-2" />
              Loading invitation records...
            </div>
          ) : pendingInvitations.length === 0 ? (
            <div className="py-8 text-center text-xs font-semibold text-text-secondary">
              No pending invitations. Click "Invite New Member" above to invite teammates.
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left font-sans border-collapse">
                <thead>
                  <tr className="border-b-2 border-brand-primary/20 text-[11px] font-bold uppercase text-text-secondary tracking-wider bg-bg-base">
                    <th className="py-3 px-4">Invited Email</th>
                    <th className="py-3 px-4">Role</th>
                    <th className="py-3 px-4">Sent Date</th>
                    <th className="py-3 px-4">Expires</th>
                    <th className="py-3 px-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y border-t-2 border-brand-primary/20">
                  {pendingInvitations.map((inv) => (
                    <tr key={inv.id} className="hover:bg-bg-base/50 transition-colors">
                      <td className="py-3.5 px-4 text-xs font-bold text-brand-primary">
                        <div className="flex items-center gap-2">
                          <Mail size={14} className="text-brand-secondary" />
                          <span>{inv.email}</span>
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-xs font-bold">
                        <span className="text-[10px] uppercase font-bold text-brand-secondary bg-brand-secondary/10 border border-brand-secondary/30 px-2 py-0.5 rounded-[3px]">
                          {inv.role}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-xs text-text-secondary font-semibold">
                        {new Date(inv.createdAt).toLocaleDateString()}
                      </td>
                      <td className="py-3.5 px-4 text-xs text-text-secondary font-semibold">
                        {new Date(inv.expiresAt).toLocaleDateString()}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button 
                            variant="outline" 
                            size="sm" 
                            onClick={() => handleResend(inv.id)}
                            className="text-[11px] py-1 px-2.5"
                          >
                            <RefreshCw size={12} className="mr-1" /> Resend
                          </Button>
                          <Button 
                            variant="secondary" 
                            size="sm" 
                            onClick={() => handleCancel(inv.id)}
                            className="text-[11px] py-1 px-2.5 bg-red-100 text-red-700 border-red-300 hover:bg-red-200"
                          >
                            <Trash2 size={12} className="mr-1" /> Revoke
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </Card>
      </div>

      {/* Invite Member Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-brand-primary/60 backdrop-blur-xs">
          <div className="w-full max-w-lg bg-white border-2 border-brand-primary rounded-[8px] shadow-hard p-6 font-sans space-y-6">
            <div className="flex items-center justify-between border-b-2 border-brand-primary/20 pb-4">
              <div>
                <h3 className="font-display text-xl text-brand-primary uppercase">INVITE NEW MEMBER</h3>
                <p className="text-xs font-semibold text-text-secondary mt-0.5">
                  Send an invitation link to grant access to {currentTenant?.name || 'this workspace'}.
                </p>
              </div>
              <button 
                onClick={() => setIsModalOpen(false)} 
                className="text-text-secondary hover:text-brand-primary font-bold text-lg"
              >
                ✕
              </button>
            </div>

            <form onSubmit={handleSendInvite} className="space-y-4">
              <Input
                label="Member Email Address"
                type="email"
                placeholder="colleague@susume.io"
                value={inviteEmail}
                onChange={(e) => setInviteEmail(e.target.value)}
                required
              />

              <div className="space-y-1.5 text-left">
                <label className="block text-xs font-bold uppercase text-brand-primary tracking-wide">
                  Assigned Permission Role
                </label>
                <select
                  value={inviteRole}
                  onChange={(e) => setInviteRole(e.target.value as 'MEMBER' | 'ADMIN')}
                  className="w-full p-3 bg-white border-2 border-brand-primary rounded-[4px] text-xs font-semibold text-brand-primary focus:bg-amber-50 outline-none"
                >
                  <option value="MEMBER">MEMBER — Can view stats, vectors, and documentation</option>
                  <option value="ADMIN">ADMIN — Full access including API key regeneration & member management</option>
                </select>
              </div>

              {createdInviteUrl && (
                <div className="p-3 border-2 border-brand-accent bg-amber-50 rounded-[4px] space-y-2">
                  <span className="text-[11px] font-bold text-brand-primary block">Shareable Invitation Link:</span>
                  <div className="flex items-center gap-2">
                    <input 
                      readOnly 
                      value={createdInviteUrl} 
                      className="w-full p-2 bg-white border border-brand-primary/30 rounded text-[11px] font-mono text-brand-primary"
                    />
                    <Button 
                      type="button" 
                      variant="outline" 
                      size="sm" 
                      onClick={() => copyToClipboard(createdInviteUrl)}
                    >
                      <Copy size={14} />
                      {copiedToken ? 'Copied' : 'Copy'}
                    </Button>
                  </div>
                </div>
              )}

              <div className="border-t-2 border-brand-primary/20 pt-4 flex items-center justify-end gap-3">
                <Button 
                  type="button" 
                  variant="outline" 
                  onClick={() => setIsModalOpen(false)}
                >
                  Cancel
                </Button>
                <Button 
                  type="submit" 
                  variant="primary" 
                  disabled={modalLoading || !inviteEmail}
                >
                  {modalLoading ? 'Sending...' : 'Send Invitation'}
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
