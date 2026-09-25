import type { CurrentUser, Role } from './SessionContext'

/**
 * TEMPORARY LIMITATION: the API has no users endpoint yet, so this list is a static
 * copy of app_users from db/seed/01_master_data.sql (fictional people). Replace it with
 * a call to the API once a users endpoint exists.
 */
export const SEED_USERS: CurrentUser[] = [
  { id: 1, name: 'Ana Paula Ribeiro', role: 'requester' },
  { id: 2, name: 'Carlos Eduardo Mendes', role: 'requester' },
  { id: 3, name: 'Fernanda Lima Castro', role: 'requester' },
  { id: 4, name: 'Rafael Augusto Nogueira', role: 'requester' },
  { id: 5, name: 'Juliana Martins Prado', role: 'requester' },
  { id: 6, name: 'Thiago Henrique Souza', role: 'requester' },
  { id: 7, name: 'Camila Ferreira Duarte', role: 'requester' },
  { id: 8, name: 'Marcos Vinícius Tavares', role: 'buyer' },
  { id: 9, name: 'Patrícia Almeida Rocha', role: 'buyer' },
  { id: 10, name: 'Roberto Carlos Pinheiro', role: 'approver' },
  { id: 11, name: 'Luciana Barros Teixeira', role: 'approver' },
  { id: 12, name: 'Eduardo Campos Moreira', role: 'finance' },
  { id: 13, name: 'Beatriz Cardoso Lopes', role: 'finance' },
  { id: 14, name: 'Henrique Batista Vasconcelos', role: 'manager' },
]

export const ROLE_ORDER: Role[] = ['requester', 'buyer', 'approver', 'finance', 'manager']