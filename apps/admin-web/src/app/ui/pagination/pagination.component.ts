import { Component, EventEmitter, Input, Output } from '@angular/core';

/** Paginación L del contrato: `page` base 0, `size` 1..100. */
@Component({
  selector: 'app-pagination',
  standalone: true,
  templateUrl: './pagination.component.html',
})
export class PaginationComponent {
  @Input() page = 0;
  @Input() size = 20;
  @Input() total = 0;
  @Output() pageChange = new EventEmitter<number>();

  get totalPages(): number {
    return Math.max(1, Math.ceil(this.total / Math.max(1, this.size)));
  }

  get desde(): number {
    return this.total === 0 ? 0 : this.page * this.size + 1;
  }

  get hasta(): number {
    return Math.min(this.total, (this.page + 1) * this.size);
  }

  anterior(): void {
    if (this.page > 0) this.pageChange.emit(this.page - 1);
  }

  siguiente(): void {
    if (this.page + 1 < this.totalPages) this.pageChange.emit(this.page + 1);
  }
}
