import { useAuth } from 'react-oidc-context';
import { jwtDecode } from 'jwt-decode';

export type AppRole = 'ADMIN' | 'OPERADOR' | 'CLIENTE';

interface CognitoIdTokenClaims {
  'cognito:groups'?: string[];
}

// Equivalente al claim "roles" que entrega Microsoft Entra ID: en Cognito
// los roles se modelan como "grupos" y viajan en el claim "cognito:groups".
export function useRoles(): AppRole[] {
  const auth = useAuth();
  const idToken = auth.user?.id_token;

  if (!idToken) return [];

  try {
    const claims = jwtDecode<CognitoIdTokenClaims>(idToken);
    const groups = claims['cognito:groups'] || [];
    return groups.map((g) => g.toUpperCase()) as AppRole[];
  } catch (e) {
    console.error('No se pudo decodificar el id_token', e);
    return [];
  }
}

export function useHasRole(role: AppRole): boolean {
  const roles = useRoles();
  return roles.includes(role);
}
