import { PersonAddressResponseDto } from './person-address-response-dto';
import { PersonContactResponseDto } from './person-contact-response-dto';

export interface Company {
  id?: number;
  person: {
    id?: number;
    tipoPessoa: 'PJ';
    name: string;
    cpfCnpj: string;
  };
  legalEntity: {
    id?: number;
    nomeFantasia?: string | null;
    inscricaoEstadual?: string | null;
  };
  contacts: PersonContactResponseDto[];
  addresses: PersonAddressResponseDto[];
}

export interface CompanyRequest {
  person: {
    cpfCnpj: string;
    tipoPessoa: 'PJ';
    name: string;
  };
  legalEntity: {
    nomeFantasia?: string | null;
    inscricaoEstadual?: string | null;
  };
  contacts: Array<{
    type: string;
    value: string;
    principal?: boolean;
    description?: string | null;
  }>;
  addresses: Array<{
    type: string;
    cep?: string | null;
    logradouro?: string | null;
    numero?: string | null;
    complemento?: string | null;
    bairro?: string | null;
    cidade?: string | null;
    uf?: string | null;
    principal?: boolean;
  }>;
}
