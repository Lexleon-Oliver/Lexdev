export interface ProductImageRequest {
  fileName: string;
  storagePath: string;
  contentType?: string | null;
  mainImage?: boolean;
  sortOrder?: number;
}
