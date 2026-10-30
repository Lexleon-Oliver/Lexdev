//go:build windows

package main

import (
	"context"
	"fmt"
	"syscall"
	"unsafe"
)

const (
	windowsServiceName = "SystemProLicenseAgent"

	serviceWin32OwnProcess = 0x00000010
	serviceStopped         = 0x00000001
	serviceStartPending    = 0x00000002
	serviceStopPending     = 0x00000003
	serviceRunning         = 0x00000004

	serviceAcceptStop     = 0x00000001
	serviceAcceptShutdown = 0x00000004

	serviceControlStop     = 0x00000001
	serviceControlShutdown = 0x00000005
)

type serviceTableEntry struct {
	serviceName *uint16
	serviceProc uintptr
}

type serviceStatus struct {
	ServiceType             uint32
	CurrentState            uint32
	ControlsAccepted        uint32
	Win32ExitCode           uint32
	ServiceSpecificExitCode uint32
	CheckPoint              uint32
	WaitHint                uint32
}

var (
	advapi32                         = syscall.NewLazyDLL("advapi32.dll")
	procStartServiceCtrlDispatcherW  = advapi32.NewProc("StartServiceCtrlDispatcherW")
	procRegisterServiceCtrlHandlerEx = advapi32.NewProc("RegisterServiceCtrlHandlerExW")
	procSetServiceStatus             = advapi32.NewProc("SetServiceStatus")

	windowsAgent        *Agent
	windowsCancel       context.CancelFunc
	serviceMainCallback = syscall.NewCallback(windowsServiceMain)
	handlerCallback     = syscall.NewCallback(windowsServiceHandler)
)

func runAsService(agent *Agent) error {
	windowsAgent = agent
	serviceName, err := syscall.UTF16PtrFromString(windowsServiceName)
	if err != nil {
		return err
	}
	table := []serviceTableEntry{
		{serviceName: serviceName, serviceProc: serviceMainCallback},
		{},
	}
	r1, _, callErr := procStartServiceCtrlDispatcherW.Call(uintptr(unsafe.Pointer(&table[0])))
	if r1 == 0 {
		return fmt.Errorf("StartServiceCtrlDispatcherW falhou: %w", callErr)
	}
	return nil
}

func windowsServiceMain(_ uint32, _ uintptr) uintptr {
	serviceName, _ := syscall.UTF16PtrFromString(windowsServiceName)
	handle, _, _ := procRegisterServiceCtrlHandlerEx.Call(
		uintptr(unsafe.Pointer(serviceName)),
		handlerCallback,
		0,
	)
	if handle == 0 {
		return 0
	}

	setWindowsServiceStatus(handle, serviceStartPending, 0, 0)
	ctx, cancel := context.WithCancel(context.Background())
	windowsCancel = cancel
	setWindowsServiceStatus(handle, serviceRunning, serviceAcceptStop|serviceAcceptShutdown, 0)

	exitCode := uint32(0)
	if windowsAgent == nil || windowsAgent.serviceLoop(ctx) != nil {
		exitCode = 1
	}
	setWindowsServiceStatus(handle, serviceStopped, 0, exitCode)
	return 0
}

func windowsServiceHandler(control, _ uint32, _ uintptr, _ uintptr) uintptr {
	switch control {
	case serviceControlStop, serviceControlShutdown:
		if windowsCancel != nil {
			windowsCancel()
		}
	}
	return 0
}

func setWindowsServiceStatus(handle uintptr, state, accepted, exitCode uint32) {
	status := serviceStatus{
		ServiceType:      serviceWin32OwnProcess,
		CurrentState:     state,
		ControlsAccepted: accepted,
		Win32ExitCode:    exitCode,
	}
	if state == serviceStopPending {
		status.WaitHint = 5000
	}
	procSetServiceStatus.Call(handle, uintptr(unsafe.Pointer(&status)))
}
