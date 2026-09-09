// Interface do cliente (pode ser movida para um arquivo models/client.model.ts)
export interface Client {
  id?: number;
  tipoPessoa: 'PF' | 'PJ';
  name: string;            // Nome ou Razão Social
  nomeFantasia?: string;   // apenas PJ
  cpfCnpj: string;
  rgIe?: string;           // RG ou Inscrição Estadual
  email: string;
  phone: string;
  cep: string;
  logradouro: string;
  numero: string;
  complemento?: string;
  bairro: string;
  cidade: string;
  uf: string;
  active: boolean;
}
