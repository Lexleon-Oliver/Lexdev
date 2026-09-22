import { BankDetailsDto } from "./bank-details-dto";
import { IndividualPersonResponseDto } from "./individual-person-response-dto";
import { LegalEntityResponseDto } from "./legal-entity-response-dto";
import { PersonAddressResponseDto } from "./person-address-response-dto";
import { PersonContactResponseDto } from "./person-contact-response-dto";
import { PersonResponseDto } from "./person-response-dto";
import { SupplierContactDto } from "./supplier-contact-dto";
import { SupplierDocumentDto } from "./supplier-document-dto";


export interface SupplierResponseDto {
  id: number;
  person: PersonResponseDto;
  individual?: IndividualPersonResponseDto | null;
  legalEntity?: LegalEntityResponseDto | null;
  contacts: PersonContactResponseDto[];
  addresses: PersonAddressResponseDto[];
  condicaoPagamentoPadrao: string;
  prazoEntregaDias: number;
  valorMinimoPedido: number;
  categoria: string;
  observacoesComerciais: string;
  bankDetails?: BankDetailsDto | null;
  contatos: SupplierContactDto[];
  documentos: SupplierDocumentDto[];
  active: boolean;
}
