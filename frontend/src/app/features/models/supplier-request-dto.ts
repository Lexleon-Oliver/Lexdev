import { BankDetailsDto } from "./bank-details-dto";
import { PersonAddressResponseDto } from "./person-address-response-dto";
import { PersonContactResponseDto } from "./person-contact-response-dto";
import { SupplierContactDto } from "./supplier-contact-dto";
import { SupplierDocumentDto } from "./supplier-document-dto";

export interface SupplierRequestDto {
  person: {
    tipoPessoa: string;
    name: string;
    cpfCnpj: string;
    individualPerson?: { rg: string };
    legalEntity?: { nomeFantasia?: string; inscricaoEstadual: string };
    contacts?: Omit<PersonContactResponseDto, 'id'>[];
    addresses?: Omit<PersonAddressResponseDto, 'id'>[];
  };
  condicaoPagamentoPadrao: string;
  prazoEntregaDias: number;
  valorMinimoPedido: number;
  categoria: string;
  observacoesComerciais: string;
  bankDetails?: BankDetailsDto;
  contatos?: SupplierContactDto[];
  documentos?: SupplierDocumentDto[];
  active?: boolean;
}
