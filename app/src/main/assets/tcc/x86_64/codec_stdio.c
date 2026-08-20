#include <stdio.h>

/*
 * ISO C 7.21.3: interactive stdout may be unbuffered or line-buffered.
 * musl does not flush stdout on scanf the way glibc does, so a textbook
 *   printf("Enter a number: ");
 *   scanf("%d", &n);
 * hid the prompt until after the user typed. Unbuffered stdout is the
 * standard-conforming interactive behaviour students expect.
 */
__attribute__((constructor))
static void codec_interactive_stdio(void)
{
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
}
