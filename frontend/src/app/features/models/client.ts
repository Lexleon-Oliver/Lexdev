import { PersonAddress } from "./person-address";
import { PersonContact } from "./person-contact";

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
  contacts: PersonContact[];
  addresses: PersonAddress[];
}
