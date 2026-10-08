import { NgbdSortableHeader, SortEvent } from './sortable.directive';

describe('NgbdSortableHeader', () => {
  let directive: NgbdSortableHeader;

  beforeEach(() => {
    directive = new NgbdSortableHeader();
    directive.sortable = 'name';
  });

  it('should create an instance', () => {
    expect(directive).toBeTruthy();
  });

  it('should rotate direction from "" to "asc" and emit sort event', () => {
    let emittedEvent: SortEvent | undefined;
    directive.sort.subscribe((event: SortEvent) => {
      emittedEvent = event;
    });

    directive.direction = '';
    directive.rotate();

    expect(directive.direction).toBe('asc');
    expect(emittedEvent).toEqual({ column: 'name', direction: 'asc' });
  });

  it('should rotate direction from "asc" to "desc"', () => {
    directive.direction = 'asc';
    directive.rotate();

    expect(directive.direction).toBe('desc');
  });

  it('should rotate direction from "desc" back to ""', () => {
    directive.direction = 'desc';
    directive.rotate();

    expect(directive.direction).toBe('');
  });
});
