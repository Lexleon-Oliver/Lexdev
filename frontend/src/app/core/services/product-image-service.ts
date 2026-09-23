import { HttpClient } from '@angular/common/http';
import { inject, Injectable, Service } from '@angular/core';
import { Observable } from 'rxjs';
import { ProductImage } from '../../features/models/product-image';

@Injectable({
  providedIn: 'root',
})
export class ProductImageService {

  private readonly http = inject(HttpClient);

  private readonly apiUrl = '/api/products';

  findAll(productId: number): Observable<ProductImage[]> {
    return this.http.get<ProductImage[]>(
      `${this.apiUrl}/${productId}/images`
    );
  }

  upload(
    productId: number,
    file: File
  ): Observable<ProductImage> {

    const formData = new FormData();

    formData.append('file', file);

    return this.http.post<ProductImage>(
      `${this.apiUrl}/${productId}/images`,
      formData
    );
  }

  getContent(
    productId: number,
    imageId: number
  ): Observable<Blob> {

    return this.http.get(
      `${this.apiUrl}/${productId}/images/${imageId}/content`,
      {
        responseType: 'blob',
      }
    );
  }

  setMainImage(
    productId: number,
    imageId: number
  ): Observable<ProductImage> {

    return this.http.post<ProductImage>(
      `${this.apiUrl}/${productId}/images/${imageId}/main`,
      {}
    );
  }

  delete(
    productId: number,
    imageId: number
  ): Observable<void> {

    return this.http.delete<void>(
      `${this.apiUrl}/${productId}/images/${imageId}`
    );
  }
}
