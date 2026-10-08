import { DateAgoPipe } from './date-ago.pipe';

describe('DateAgoPipe', () => {
  let pipe: DateAgoPipe;

  beforeEach(() => {
    pipe = new DateAgoPipe();
  });

  it('create an instance', () => {
    expect(pipe).toBeTruthy();
  });

  it('should return input as is if falsey or null', () => {
    expect(pipe.transform(null)).toBeNull();
    expect(pipe.transform('')).toBe('');
    expect(pipe.transform(undefined)).toBeUndefined();
  });

  it('should return "Just now" for dates less than 29 seconds ago', () => {
    const recent = new Date(Date.now() - 10 * 1000);
    expect(pipe.transform(recent)).toBe('Just now');
  });

  it('should return singular and plural for minutes', () => {
    const oneMinAgo = new Date(Date.now() - 65 * 1000);
    expect(pipe.transform(oneMinAgo)).toBe('1 minute ago');

    const twoMinsAgo = new Date(Date.now() - 130 * 1000);
    expect(pipe.transform(twoMinsAgo)).toBe('2 minutes ago');
  });

  it('should return singular and plural for hours', () => {
    const oneHourAgo = new Date(Date.now() - 3700 * 1000);
    expect(pipe.transform(oneHourAgo)).toBe('1 hour ago');

    const twoHoursAgo = new Date(Date.now() - 7500 * 1000);
    expect(pipe.transform(twoHoursAgo)).toBe('2 hours ago');
  });

  it('should return singular and plural for days', () => {
    const oneDayAgo = new Date(Date.now() - 86400 * 1000 - 1000);
    expect(pipe.transform(oneDayAgo)).toBe('1 day ago');

    const threeDaysAgo = new Date(Date.now() - 3 * 86400 * 1000 - 1000);
    expect(pipe.transform(threeDaysAgo)).toBe('3 days ago');
  });

  it('should return singular and plural for weeks', () => {
    const oneWeekAgo = new Date(Date.now() - 7 * 86400 * 1000 - 1000);
    expect(pipe.transform(oneWeekAgo)).toBe('1 week ago');

    const twoWeeksAgo = new Date(Date.now() - 14 * 86400 * 1000 - 1000);
    expect(pipe.transform(twoWeeksAgo)).toBe('2 weeks ago');
  });

  it('should return singular and plural for months', () => {
    const oneMonthAgo = new Date(Date.now() - 31 * 86400 * 1000);
    expect(pipe.transform(oneMonthAgo)).toBe('1 month ago');

    const twoMonthsAgo = new Date(Date.now() - 65 * 86400 * 1000);
    expect(pipe.transform(twoMonthsAgo)).toBe('2 months ago');
  });

  it('should return singular and plural for years', () => {
    const oneYearAgo = new Date(Date.now() - 370 * 86400 * 1000);
    expect(pipe.transform(oneYearAgo)).toBe('1 year ago');

    const twoYearsAgo = new Date(Date.now() - 750 * 86400 * 1000);
    expect(pipe.transform(twoYearsAgo)).toBe('2 years ago');
  });
});
