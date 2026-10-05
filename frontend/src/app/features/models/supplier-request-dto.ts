import { BankDetailsDto } from "./bank-details-dto";
import { PersonAddressResponseDto } from "./person-address-response-dto";
import { PersonContactResponseDto } from "./person-contact-response-dto";
import { SupplierContactDto } from "./supplier-contact-dto";


export interface SupplierRequestDto {
  person: {
    tipoPessoa: string;
    name: string;
    cpfCnpj: string;
  };
  individual?: { rg: string } | null;
  legalEntity?: {
    nomeFantasia?: string;
    inscricaoEstadual: string;
  } | null;
  contacts?: Omit<PersonContactResponseDto, 'id'>[];
  addresses?: Omit<PersonAddressResponseDto, 'id'>[];
  condicaoPagamentoPadrao: string;
  prazoEntregaDias: number;
  valorMinimoPedido: number;
  categoria: string;
  observacoesComerciais: string;
  bankDetails?: BankDetailsDto | null;
  contatos?: SupplierContactDto[];
}
