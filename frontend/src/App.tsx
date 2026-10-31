import { useAuth } from 'react-oidc-context';
import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { Layout } from './components/Layout';
import { DashboardPage } from './pages/DashboardPage';
import { OrdersPage } from './pages/OrdersPage';
import { CatalogPage } from './pages/CatalogPage';

function LoginPage() {
  const auth = useAuth();

  if (auth.isLoading) {
    return (
      <div className="container text-center mt-5">
        <p className="fs-4 text-secondary">Cargando sesion...</p>
      </div>
    );
  }

  if (auth.error) {
    return (
      <div className="container text-center mt-5">
        <p className="text-danger fs-5">Error de autenticacion: {auth.error.message}</p>
        <button className="btn btn-primary mt-2" onClick={() => auth.signinRedirect()}>
          Reintentar inicio de sesion
        </button>
      </div>
    );
  }

  if (auth.isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return (
    <div className="container text-center mt-5">
      <div className="card shadow p-5 mx-auto" style={{ maxWidth: '480px' }}>
        <h2 className="mb-3 text-primary">Pedidos360</h2>
        <p className="text-muted mb-4">
          Inicia sesion con tu cuenta de AWS Cognito para acceder al sistema.
        </p>
        <button className="btn btn-primary btn-lg w-100" onClick={() => auth.signinRedirect()}>
          Iniciar sesion
        </button>
      </div>
    </div>
  );
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<LoginPage />} />

      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <Layout>
              <DashboardPage />
            </Layout>
          </ProtectedRoute>
        }
      />

      <Route
        path="/orders"
        element={
          <ProtectedRoute allowedRoles={['ADMIN', 'OPERADOR', 'CLIENTE']}>
            <Layout>
              <OrdersPage />
            </Layout>
          </ProtectedRoute>
        }
      />

      <Route
        path="/catalog"
        element={
          <ProtectedRoute allowedRoles={['ADMIN', 'OPERADOR', 'CLIENTE']}>
            <Layout>
              <CatalogPage />
            </Layout>
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
