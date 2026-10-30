//go:build !windows

package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"
)

func runAsService(agent *Agent) error {
	ctx, cancel := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer cancel()
	return agent.serviceLoop(ctx)
}
