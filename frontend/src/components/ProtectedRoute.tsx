/*
 * TP: Used Cursor to implement protected route component with authentication check
 */

import { useEffect, useState, type ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { isAuthenticated } from '../utils/auth';

type ProtectedRouteProps = {
  children: ReactElement;
};

export default function ProtectedRoute({ children }: ProtectedRouteProps) {
  const [loading, setLoading] = useState(true);
  const [authed, setAuthed] = useState(false);
  const location = useLocation();

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const ok = await isAuthenticated();
        if (!mounted) return;
        // Explicitly set authenticated to false if check fails
        setAuthed(ok === true);
      } catch (error) {
        if (!mounted) return;
        console.error('Auth check error:', error);
        setAuthed(false);
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => { mounted = false; };
  }, [location.pathname]);

  if (loading) {
    return <div style={{ padding: 24 }} />; // minimal placeholder
  }

  if (!authed) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return children;
}


