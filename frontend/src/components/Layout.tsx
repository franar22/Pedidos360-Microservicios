import type { ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { Link } from 'react-router-dom';
import { useRoles } from '../auth/useRoles';

export function Layout({ children }: { children: ReactNode }) {
  const auth = useAuth();
  const roles = useRoles();

  const handleSignOut = () => {
    auth.removeUser();
  };

  return (
    <div>
      <header className="navbar navbar-dark bg-dark px-4 mb-4 shadow-sm">
        <span className="navbar-brand mb-0 h1">Pedidos360</span>
        <nav className="d-flex gap-3">
          <Link className="nav-link text-light" to="/dashboard">
            Dashboard
          </Link>
          <Link className="nav-link text-light" to="/orders">
            Pedidos
          </Link>
          <Link className="nav-link text-light" to="/catalog">
            Catalogo
          </Link>
        </nav>
        <div className="d-flex align-items-center gap-3">
          <span className="text-light small">
            {auth.user?.profile.email} ({roles.join(', ') || 'sin rol'})
          </span>
          <button className="btn btn-outline-light btn-sm" onClick={handleSignOut}>
            Cerrar sesion
          </button>
        </div>
      </header>
      <main className="container">{children}</main>
    </div>
  );
}
