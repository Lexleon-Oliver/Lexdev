export interface ClientSummary {
  id: number;
  active: boolean;
  person: {
    id: number;
    tipoPessoa: 'PF' | 'PJ';
    name: string;
    cpfCnpj: string;
  };
  email: string | null;
  phone: string | null;
}
