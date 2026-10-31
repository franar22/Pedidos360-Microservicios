import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';
import 'bootstrap/dist/css/bootstrap-grid.min.css';
import 'bootstrap/dist/css/bootstrap.min.css';
import { AuthProvider } from 'react-oidc-context';
import { BrowserRouter } from 'react-router-dom';

// Configuracion de Cognito. Reemplaza estos valores por los reales una vez
// que crees tu User Pool (ver README, seccion "Cognito").
const cognitoAuthConfig = {
  authority:
    import.meta.env.VITE_COGNITO_AUTHORITY ||
    'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX',
  client_id: import.meta.env.VITE_COGNITO_CLIENT_ID || 'tu_app_client_id',
  redirect_uri: window.location.origin, // funciona en localhost y en Amplify
  response_type: 'code',
  scope: 'phone openid email',
};

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider {...cognitoAuthConfig}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </AuthProvider>
  </StrictMode>
);
