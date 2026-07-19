#import <Foundation/Foundation.h>
#include <string>

NSString *ToNSString(const std::string &value) {
    return [NSString stringWithUTF8String:value.c_str()];
}
