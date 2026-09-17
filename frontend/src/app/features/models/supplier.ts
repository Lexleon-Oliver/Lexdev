import { SupplierContact } from "./supplier-contact";
import { TipoConta } from "./tipo-conta";
import { TipoPessoa } from "./tipo-pessoa";

export interface Supplier {
  id?: number;
  tipoPessoa: 'PF' | 'PJ';
  name: string;
  nomeFantasia?: string;
  cpfCnpj: string;
  rgIe?: string;
  email: string;
  phone: string;
  ativo: boolean;

  // Endereço
  cep?: string;
  logradouro?: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  cidade?: string;
  uf?: string;

  // Comercial
  categoria?: string;
  condicaoPagamento?: string;
  prazoEntrega?: number | null;
  valorMinimoPedido?: number | null;
  observacoesComerciais?: string;

  // Bancário
  banco?: string;
  agencia?: string;
  conta?: string;
  tipoConta?: TipoConta | '';
  chavePix?: string;

  // Contatos
  contatos?: SupplierContact[];
}
