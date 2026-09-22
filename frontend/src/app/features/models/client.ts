import { PersonAddressResponseDto } from "./person-address-response-dto";
import { PersonContactResponseDto } from "./person-contact-response-dto";


// Interface do cliente (pode ser movida para um arquivo models/client.model.ts)
export interface Client {
  id?: number;
  active?: boolean;
  person: {
    id?: number;
    tipoPessoa: 'PF' | 'PJ';
    name: string;
    cpfCnpj: string;
  };
  individual?: {
    id?: number;
    rg?: string;
  } | null;
  legalEntity?: {
    id?: number;
    nomeFantasia?: string;
    inscricaoEstadual?: string;
  } | null;
  contacts: PersonContactResponseDto[];
  addresses: PersonAddressResponseDto[];
}
