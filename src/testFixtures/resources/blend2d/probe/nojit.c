#include <stdlib.h>
#include "blend2d.h"
BLResult __real_blContextInitAs(BLContextCore* self, BLImageCore* image, const BLContextCreateInfo* cci);
BLResult __wrap_blContextInitAs(BLContextCore* self, BLImageCore* image, const BLContextCreateInfo* cci) {
	BLContextCreateInfo copy = *cci;
	if (getenv("B2D_NO_JIT")) copy.flags |= BL_CONTEXT_CREATE_FLAG_DISABLE_JIT;
	return __real_blContextInitAs(self, image, &copy);
}
