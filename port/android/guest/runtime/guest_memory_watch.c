/*
GUEST_MEMORY_WATCH.C

Guest memory write tracking (port/linux/src/memory_watch.c's interface).
Page protection faults are delivered to the host, which owns signal
handling in the Android process, so the tracking itself lives there
(port/android/host/host_memory.c).
*/

#include "platform.h"
#include "guest_host.h"

#define WATCH_PAGE_SIZE 0x1000UL
#define WATCH_PAGE_COUNT (PLATFORM_CONTIGUOUS_SIZE / WATCH_PAGE_SIZE)

static volatile unsigned int *watch_state;

static unsigned long watch_page(unsigned long address)
{
	return (address - PLATFORM_CONTIGUOUS_BASE) / WATCH_PAGE_SIZE;
}

void memory_watch_initialize(void)
{
	host_memory_watch_initialize();
	watch_state = (volatile unsigned int *)(unsigned long)host_memory_watch_state();
}

void memory_watch_protect(unsigned long address, unsigned long size)
{
	host_memory_watch_protect(address, size);
}

unsigned long memory_watch_generation(unsigned long address, unsigned long size)
{
	unsigned long first, last, page, newest = 0;

	if (!watch_state || !size || !platform_is_contiguous((void *)address))
		return 0;
	first = watch_page(address);
	last = watch_page(address + size - 1);
	if (last >= WATCH_PAGE_COUNT)
		last = WATCH_PAGE_COUNT - 1;
	for (page = first; page <= last; page++)
	{
		unsigned long generation = watch_state[1 + page];

		if (generation > newest)
			newest = generation;
	}
	return newest;
}

unsigned long memory_watch_serial(void)
{
	return watch_state ? watch_state[0] : host_memory_watch_serial();
}

void memory_watch_prepare_write(void *address, unsigned long size)
{
	host_memory_watch_prepare_write((unsigned int)address, size);
}

void memory_watch_forget(void *address, unsigned long size)
{
	host_memory_watch_forget((unsigned int)address, size);
}
