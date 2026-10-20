import { TestBed } from '@angular/core/testing';
import { DemoImageComponent } from './demo-image.component';

describe('DemoImageComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DemoImageComponent],
    }).compileComponents();
  });

  it('mantiene el alt y cae a un placeholder neutro si la imagen falla', () => {
    const fixture = TestBed.createComponent(DemoImageComponent);
    fixture.componentRef.setInput('src', '/assets/demo/no-existe.jpg');
    fixture.componentRef.setInput('alt', 'Habitación de demostración');
    fixture.detectChanges();

    const img = fixture.nativeElement.querySelector('img') as HTMLImageElement;
    expect(img.getAttribute('alt')).toBe('Habitación de demostración');
    expect(img.getAttribute('src')).toBe('/assets/demo/no-existe.jpg');

    img.dispatchEvent(new Event('error'));
    fixture.detectChanges();

    expect(img.getAttribute('src')).toBe('/assets/demo/placeholder.svg');
    expect(img.getAttribute('alt')).toBe('Habitación de demostración');
    expect(img.classList.contains('imagen--fallback')).toBe(true);
  });
});
