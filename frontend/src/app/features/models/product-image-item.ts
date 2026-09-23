export interface ProductImageItem {

  key: string;

  id?: number;

  fileName: string;

  contentType?: string | null;

  fileSize?: number;

  mainImage: boolean;

  sortOrder: number;

  url?: string;

  previewUrl: string;

  pendingFile?: File;
}
