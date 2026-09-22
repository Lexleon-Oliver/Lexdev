export interface ProductImage {
  id?: number;
  fileName: string;
  storagePath: string;
  contentType?: string | null;
  mainImage: boolean;
  sortOrder: number;
}
