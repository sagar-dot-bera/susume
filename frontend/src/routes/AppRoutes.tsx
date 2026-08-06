import React from 'react';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LandingPage } from '../features/authentication/LandingPage';
import { TenantRegistration } from '../features/authentication/TenantRegistration';
import { SignUp } from '../features/authentication/SignUp';
import { Login } from '../features/authentication/Login';
import { InvitationLanding } from '../features/authentication/InvitationLanding';
import { JoinScreen } from '../features/authentication/JoinScreen';
import { DashboardLayout } from '../features/dashboard/DashboardLayout';
import { DashboardHome } from '../features/dashboard/DashboardHome';
import { TeamMembers } from '../features/dashboard/TeamMembers';
import { ApiKeyManager } from '../features/dashboard/ApiKeyManager';
import { ApiDocs } from '../features/dashboard/ApiDocs';
import { StatsUsage } from '../features/dashboard/StatsUsage';
import { StrategyManagement } from '../features/dashboard/StrategyManagement';
import { isAuthenticated } from '../services/api';

/** Redirects unauthenticated users to the login page. */
const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  if (!isAuthenticated()) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

export const AppRoutes: React.FC = () => {
  return (
    <HashRouter>
      <Routes>
        {/* Public Landing & Authentication */}
        <Route path="/" element={<LandingPage />} />
        <Route path="/register" element={<TenantRegistration />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/login" element={<Login />} />
        
        {/* Public Invitation & Registration Flows */}
        <Route path="/invitation/:token" element={<InvitationLanding />} />
        <Route path="/invite/:token" element={<InvitationLanding />} />
        <Route path="/join/:token" element={<JoinScreen />} />

        {/* Dashboard Isolated Area — requires valid JWT */}
        <Route 
          path="/dashboard/*" 
          element={
            <ProtectedRoute>
              <DashboardLayout>
                <Routes>
                  <Route path="home" element={<DashboardHome />} />
                  <Route path="strategy" element={<StrategyManagement />} />
                  <Route path="team" element={<TeamMembers />} />
                  <Route path="keys" element={<ApiKeyManager />} />
                  <Route path="docs" element={<ApiDocs />} />
                  <Route path="stats" element={<StatsUsage />} />
                  <Route path="*" element={<Navigate to="home" replace />} />
                </Routes>
              </DashboardLayout>
            </ProtectedRoute>
          } 
        />

        {/* Catch-all Redirect */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </HashRouter>
  );
};
