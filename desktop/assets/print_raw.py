import sys
import ctypes
from ctypes import wintypes

# Define DOC_INFO_1 Structure
class DOC_INFO_1(ctypes.Structure):
    _fields_ = [
        ("pDocName", wintypes.LPCWSTR),
        ("pOutputFile", wintypes.LPCWSTR),
        ("pDatatype", wintypes.LPCWSTR),
    ]

def print_raw_bytes(printer_name: str, doc_name: str, data: bytes) -> bool:
    winspool = ctypes.WinDLL("winspool.drv")
    
    # Configure function prototypes
    winspool.OpenPrinterW.argtypes = [wintypes.LPCWSTR, ctypes.POINTER(wintypes.HANDLE), ctypes.c_void_p]
    winspool.OpenPrinterW.restype = wintypes.BOOL

    winspool.ClosePrinter.argtypes = [wintypes.HANDLE]
    winspool.ClosePrinter.restype = wintypes.BOOL

    winspool.StartDocPrinterW.argtypes = [wintypes.HANDLE, wintypes.DWORD, ctypes.POINTER(DOC_INFO_1)]
    winspool.StartDocPrinterW.restype = wintypes.DWORD

    winspool.EndDocPrinter.argtypes = [wintypes.HANDLE]
    winspool.EndDocPrinter.restype = wintypes.BOOL

    winspool.StartPagePrinter.argtypes = [wintypes.HANDLE]
    winspool.StartPagePrinter.restype = wintypes.BOOL

    winspool.EndPagePrinter.argtypes = [wintypes.HANDLE]
    winspool.EndPagePrinter.restype = wintypes.BOOL

    winspool.WritePrinter.argtypes = [wintypes.HANDLE, ctypes.c_char_p, wintypes.DWORD, ctypes.POINTER(wintypes.DWORD)]
    winspool.WritePrinter.restype = wintypes.BOOL

    handle = wintypes.HANDLE()
    if not winspool.OpenPrinterW(printer_name, ctypes.byref(handle), None):
        err = ctypes.GetLastError()
        raise RuntimeError(f"Gagal membuka printer '{printer_name}'. Windows Error Code: {err}")

    try:
        doc_info = DOC_INFO_1(pDocName=doc_name, pOutputFile=None, pDatatype="RAW")
        job_id = winspool.StartDocPrinterW(handle, 1, ctypes.byref(doc_info))
        if job_id == 0:
            err = ctypes.GetLastError()
            raise RuntimeError(f"Gagal memulai dokumen print (StartDocPrinter). Error Code: {err}")

        try:
            if not winspool.StartPagePrinter(handle):
                err = ctypes.GetLastError()
                raise RuntimeError(f"Gagal memulai halaman (StartPagePrinter). Error Code: {err}")

            written = wintypes.DWORD(0)
            if not winspool.WritePrinter(handle, data, len(data), ctypes.byref(written)):
                err = ctypes.GetLastError()
                raise RuntimeError(f"Gagal menulis ke printer (WritePrinter). Error Code: {err}")

            winspool.EndPagePrinter(handle)
        finally:
            winspool.EndDocPrinter(handle)
    finally:
        winspool.ClosePrinter(handle)

    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python print_raw.py <printer_name> [bin_file_path]")
        sys.exit(1)

    p_name = sys.argv[1]
    if len(sys.argv) >= 3:
        with open(sys.argv[2], "rb") as f:
            raw_data = f.read()
    else:
        # Read from stdin
        raw_data = sys.stdin.buffer.read()

    try:
        print_raw_bytes(p_name, "Nota Air Kos Manyar", raw_data)
        print("SUCCESS")
    except Exception as e:
        print(f"ERROR: {e}", file=sys.stderr)
        sys.exit(2)
