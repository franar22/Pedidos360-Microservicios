import type { ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { Navigate } from 'react-router-dom';
import { useRoles } from './useRoles';
import type { AppRole } from './useRoles';

interface ProtectedRouteProps {
  children: ReactNode;
  allowedRoles?: AppRole[]; // si se omite, solo exige estar autenticado
}

// Este componente cumple el rol del "Guard" que pide la guia: evita el acceso
// a rutas que requieren autenticacion (y opcionalmente, un rol especifico).
export function ProtectedRoute({ children, allowedRoles }: ProtectedRouteProps) {
  const auth = useAuth();
  const roles = useRoles();

  if (auth.isLoading) {
    return (
      <div className="container text-center mt-5">
        <p className="fs-4 text-secondary">Cargando sesion...</p>
      </div>
    );
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  if (allowedRoles && !allowedRoles.some((r) => roles.includes(r))) {
    return (
      <div className="container text-center mt-5">
        <div className="alert alert-danger">
          No tienes permisos para acceder a esta seccion.
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
