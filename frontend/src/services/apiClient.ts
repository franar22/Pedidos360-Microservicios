import axios from 'axios';

// URL de API Gateway (BFF detras). NO incluir /api/... aca.
const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const AUTHORITY =
  import.meta.env.VITE_COGNITO_AUTHORITY ||
  'https://cognito-idp.us-east-1.amazonaws.com/us-east-1_XXXXXXXXX';
const CLIENT_ID = import.meta.env.VITE_COGNITO_CLIENT_ID || 'tu_app_client_id';

export const apiClient = axios.create({
  baseURL: BASE_URL.replace(/\/$/, ''),
});

// Esto cumple el rol de "MsalInterceptor": adjunta automaticamente
// Authorization: Bearer <access_token> a cada llamada hacia el BFF/API Gateway.
apiClient.interceptors.request.use((config) => {
  const oidcStorageKey = `oidc.user:${AUTHORITY}:${CLIENT_ID}`;
  const oidcData = sessionStorage.getItem(oidcStorageKey) || localStorage.getItem(oidcStorageKey);

  if (oidcData) {
    try {
      const user = JSON.parse(oidcData);
      const token = user.id_token || user.access_token;
      if (token) {
        config.headers = config.headers ?? {};
        config.headers.Authorization = `Bearer ${token}`;
      }
    } catch (e) {
      console.error('Error al parsear el usuario OIDC almacenado', e);
    }
  } else {
    console.warn('No se encontro sesion de Cognito en storage.');
  }

  return config;
});
