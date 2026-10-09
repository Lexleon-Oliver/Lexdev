import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DeletedRecords } from './deleted-records';

describe('DeletedRecords', () => {
  let component: DeletedRecords;
  let fixture: ComponentFixture<DeletedRecords>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DeletedRecords]
    })
      .compileComponents();

    fixture = TestBed.createComponent(DeletedRecords);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
